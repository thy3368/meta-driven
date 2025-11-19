package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.cqrs.CommandService;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.lob.commands.CancelOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.commands.PlaceOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.commands.QueryOrderBookCommand;
import com.tanggo.fund.metadriven.lwc.lob.domain.OrderSide;
import com.tanggo.fund.metadriven.lwc.lob.domain.Trade;
import com.tanggo.fund.metadriven.lwc.lob.handlers.CancelOrderCommandHandler;
import com.tanggo.fund.metadriven.lwc.lob.handlers.PlaceOrderCommandHandler;
import com.tanggo.fund.metadriven.lwc.lob.service.OrderBookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LOB (Limit Order Book) 测试类
 * 使用 Spring XML 配置进行依赖注入
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:lob-test-context.xml")
class LobServiceTest {

    @Autowired
    private CommandService commandService;

    @Autowired
    private OrderBookService orderBookService;

    /**
     * 测试场景1：基本下单流程
     */
    @Test
    void testPlaceOrder() {
        // 创建买单命令
        PlaceOrderCommand buyCommand = new PlaceOrderCommand("ORDER-001", "BTCUSDT", OrderSide.BUY, new BigDecimal("50000.00"), new BigDecimal("1.0"));

        // 执行命令
        CommandResult result = commandService.handleCommand(buyCommand);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertInstanceOf(PlaceOrderCommandHandler.PlaceOrderResult.class, result.getDate());

        PlaceOrderCommandHandler.PlaceOrderResult orderResult = (PlaceOrderCommandHandler.PlaceOrderResult) result.getDate();

        assertTrue(orderResult.isSuccess());
        assertEquals("ORDER-001", orderResult.getOrder().getOrderId());
        assertEquals(0, orderResult.getTrades().size()); // 无对手盘，无成交

        System.out.println("✅ 下单成功: " + orderResult);
    }

    /**
     * 测试场景2：订单撮合（价格匹配）
     */
    @Test
    void testOrderMatching() {
        String symbol = "ETHUSDT";

        // 1. 先下一个卖单 @ 3000
        PlaceOrderCommand sellCommand = new PlaceOrderCommand("SELL-001", symbol, OrderSide.SELL, new BigDecimal("3000.00"), new BigDecimal("10.0"));
        CommandResult sellResult = commandService.handleCommand(sellCommand);
        PlaceOrderCommandHandler.PlaceOrderResult sellOrderResult = (PlaceOrderCommandHandler.PlaceOrderResult) sellResult.getDate();

        assertEquals(0, sellOrderResult.getTrades().size()); // 无对手盘
        System.out.println("✅ 卖单挂单成功: " + sellOrderResult.getOrder());

        // 2. 下一个买单 @ 3000（价格匹配，应该成交）
        PlaceOrderCommand buyCommand = new PlaceOrderCommand("BUY-001", symbol, OrderSide.BUY, new BigDecimal("3000.00"), new BigDecimal("5.0"));
        CommandResult buyResult = commandService.handleCommand(buyCommand);
        PlaceOrderCommandHandler.PlaceOrderResult buyOrderResult = (PlaceOrderCommandHandler.PlaceOrderResult) buyResult.getDate();

        // 验证成交
        assertEquals(1, buyOrderResult.getTrades().size());
        Trade trade = buyOrderResult.getTrades().get(0);
        assertEquals("BUY-001", trade.getBuyOrderId());
        assertEquals("SELL-001", trade.getSellOrderId());
        assertEquals(new BigDecimal("3000.00"), trade.getPrice());
        assertEquals(new BigDecimal("5.0"), trade.getQuantity());

        System.out.println("✅ 订单撮合成功: " + trade);
    }

    /**
     * 测试场景3：部分成交
     */
    @Test
    void testPartialFill() {
        String symbol = "BNBUSDT";

        // 1. 卖单 100 @ 500
        PlaceOrderCommand sellCommand = new PlaceOrderCommand("SELL-002", symbol, OrderSide.SELL, new BigDecimal("500.00"), new BigDecimal("100.0"));
        commandService.handleCommand(sellCommand);

        // 2. 买单 150 @ 500（只能成交100）
        PlaceOrderCommand buyCommand = new PlaceOrderCommand("BUY-002", symbol, OrderSide.BUY, new BigDecimal("500.00"), new BigDecimal("150.0"));
        CommandResult buyResult = commandService.handleCommand(buyCommand);
        PlaceOrderCommandHandler.PlaceOrderResult buyOrderResult = (PlaceOrderCommandHandler.PlaceOrderResult) buyResult.getDate();

        // 验证部分成交
        assertEquals(1, buyOrderResult.getTrades().size());
        assertEquals(new BigDecimal("100.0"), buyOrderResult.getTrades().get(0).getQuantity());

        // 买单剩余50应该挂在订单薄上
        assertEquals(new BigDecimal("50.0"), buyOrderResult.getOrder().getRemainingQuantity());

        System.out.println("✅ 部分成交测试成功: " + buyOrderResult.getOrder());
    }

    /**
     * 测试场景4：撤单
     */
    @Test
    void testCancelOrder() {
        String symbol = "ADAUSDT";

        // 1. 下单
        PlaceOrderCommand placeCommand = new PlaceOrderCommand("ORDER-CANCEL-001", symbol, OrderSide.BUY, new BigDecimal("1.50"), new BigDecimal("1000.0"));
        commandService.handleCommand(placeCommand);

        // 2. 撤单
        CancelOrderCommand cancelCommand = new CancelOrderCommand("ORDER-CANCEL-001", symbol);
        CommandResult cancelResult = commandService.handleCommand(cancelCommand);
        CancelOrderCommandHandler.CancelOrderResult cancelOrderResult = (CancelOrderCommandHandler.CancelOrderResult) cancelResult.getDate();

        assertTrue(cancelOrderResult.isSuccess());
        assertEquals("ORDER-CANCEL-001", cancelOrderResult.getOrderId());

        System.out.println("✅ 撤单成功: " + cancelOrderResult);
    }

    /**
     * 测试场景5：查询订单薄深度
     */
    @Test
    void testQueryOrderBook() {
        String symbol = "SOLUSDT";

        // 添加多个买单
        for (int i = 1; i <= 5; i++) {
            PlaceOrderCommand cmd = new PlaceOrderCommand("BUY-" + i, symbol, OrderSide.BUY, new BigDecimal(100 - i),  // 99, 98, 97, 96, 95
                    new BigDecimal("10.0"));
            commandService.handleCommand(cmd);
        }

        // 添加多个卖单
        for (int i = 1; i <= 5; i++) {
            PlaceOrderCommand cmd = new PlaceOrderCommand("SELL-" + i, symbol, OrderSide.SELL, new BigDecimal(101 + i),  // 102, 103, 104, 105, 106
                    new BigDecimal("10.0"));
            commandService.handleCommand(cmd);
        }

        // 查询订单薄
        QueryOrderBookCommand queryCommand = new QueryOrderBookCommand(symbol, 5);
        CommandResult queryResult = commandService.handleCommand(queryCommand);

        OrderBookService.OrderBookSnapshot snapshot = (OrderBookService.OrderBookSnapshot) queryResult.getDate();

        // 验证
        assertEquals(5, snapshot.getBids().size());
        assertEquals(5, snapshot.getAsks().size());

        // 买单应该按价格降序排列（最高价优先）
        assertEquals(new BigDecimal("99"), snapshot.getBids().get(0).getPrice());
        assertEquals(new BigDecimal("95"), snapshot.getBids().get(4).getPrice());

        // 卖单应该按价格升序排列（最低价优先）
        assertEquals(new BigDecimal("102"), snapshot.getAsks().get(0).getPrice());
        assertEquals(new BigDecimal("106"), snapshot.getAsks().get(4).getPrice());

        System.out.println("✅ 订单薄查询成功:");
        System.out.println(snapshot);
    }

    /**
     * 测试场景6：复杂撮合场景（多档位成交）
     */
    @Test
    void testComplexMatching() {
        String symbol = "DOGEUSDT";

        // 卖方：在不同价位挂单
        commandService.handleCommand(new PlaceOrderCommand("SELL-A", symbol, OrderSide.SELL, new BigDecimal("0.10"), new BigDecimal("1000")));
        commandService.handleCommand(new PlaceOrderCommand("SELL-B", symbol, OrderSide.SELL, new BigDecimal("0.11"), new BigDecimal("2000")));
        commandService.handleCommand(new PlaceOrderCommand("SELL-C", symbol, OrderSide.SELL, new BigDecimal("0.12"), new BigDecimal("3000")));

        // 买方：市价吃单（价格设置很高，能吃掉所有卖单）
        PlaceOrderCommand bigBuyOrder = new PlaceOrderCommand("BUY-BIG", symbol, OrderSide.BUY, new BigDecimal("0.15"),  // 高于所有卖单价格
                new BigDecimal("5000")   // 数量足够吃掉前两档
        );

        CommandResult result = commandService.handleCommand(bigBuyOrder);
        PlaceOrderCommandHandler.PlaceOrderResult orderResult = (PlaceOrderCommandHandler.PlaceOrderResult) result.getDate();

        // 应该有3笔成交（吃掉3档卖单）
        assertEquals(3, orderResult.getTrades().size());

        // 验证成交价格按时间优先
        assertEquals(new BigDecimal("0.10"), orderResult.getTrades().get(0).getPrice());
        assertEquals(new BigDecimal("0.11"), orderResult.getTrades().get(1).getPrice());
        assertEquals(new BigDecimal("0.12"), orderResult.getTrades().get(2).getPrice());

        // 验证成交数量
        BigDecimal totalFilled = orderResult.getTrades().stream().map(Trade::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("5000"), totalFilled);

        System.out.println("✅ 复杂撮合测试成功:");
        orderResult.getTrades().forEach(trade -> System.out.println("  " + trade));
    }

    /**
     * 测试场景7：价格-时间优先验证
     */
    @Test
    void testPriceTimePriority() {
        String symbol = "LINKUSDT";

        // 同一价格，时间优先
        commandService.handleCommand(new PlaceOrderCommand("SELL-TIME-1", symbol, OrderSide.SELL, new BigDecimal("20.00"), new BigDecimal("10")));
        commandService.handleCommand(new PlaceOrderCommand("SELL-TIME-2", symbol, OrderSide.SELL, new BigDecimal("20.00"), new BigDecimal("10")));

        // 买单匹配
        PlaceOrderCommand buyCommand = new PlaceOrderCommand("BUY-TIME", symbol, OrderSide.BUY, new BigDecimal("20.00"), new BigDecimal("5"));
        CommandResult result = commandService.handleCommand(buyCommand);
        PlaceOrderCommandHandler.PlaceOrderResult orderResult = (PlaceOrderCommandHandler.PlaceOrderResult) result.getDate();

        // 应该只匹配第一个卖单（时间优先）
        assertEquals(1, orderResult.getTrades().size());
        assertEquals("SELL-TIME-1", orderResult.getTrades().get(0).getSellOrderId());

        System.out.println("✅ 价格-时间优先验证成功");
    }
}
