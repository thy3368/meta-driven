package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.cqrs.CommandService;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.lob.commands.CancelOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.commands.PlaceOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.commands.QueryOrderBookCommand;
import com.tanggo.fund.metadriven.lwc.lob.domain.repo.OrderBookSnapshot;
import com.tanggo.fund.metadriven.lwc.lob.domain.OrderSide;
import com.tanggo.fund.metadriven.lwc.lob.domain.Trade;
import com.tanggo.fund.metadriven.lwc.lob.commands.CancelOrderResult;
import com.tanggo.fund.metadriven.lwc.lob.commands.PlaceOrderResult;
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
     * 辅助方法：创建下单命令
     */
    private Command createPlaceOrderCommand(String orderId, String symbol, OrderSide side,
                                           BigDecimal price, BigDecimal quantity) {
        Command command = new Command();
        command.setMethodName("placeOrder");
        command.setParam(new PlaceOrderCommand(orderId, symbol, side, price, quantity));
        return command;
    }

    /**
     * 辅助方法：创建撤单命令
     */
    private Command createCancelOrderCommand(String orderId, String symbol) {
        Command command = new Command();
        command.setMethodName("cancelOrder");
        command.setParam(new CancelOrderCommand(orderId, symbol));
        return command;
    }

    /**
     * 辅助方法：创建查询订单薄命令
     */
    private Command createQueryOrderBookCommand(String symbol, Integer depth) {
        Command command = new Command();
        command.setMethodName("queryOrderBook");
        command.setParam(new QueryOrderBookCommand(symbol, depth));
        return command;
    }

    /**
     * 测试场景1：基本下单流程
     */
    @Test
    void testPlaceOrder() {
        // 创建买单命令
        Command buyCommand = createPlaceOrderCommand("ORDER-001", "BTCUSDT", OrderSide.BUY,
            new BigDecimal("50000.00"), new BigDecimal("1.0"));

        // 执行命令
        CommandResult result = commandService.handleCommand(buyCommand);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertInstanceOf(PlaceOrderResult.class, result.getDate());

        PlaceOrderResult orderResult = (PlaceOrderResult) result.getDate();

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
        Command sellCommand = createPlaceOrderCommand("SELL-001", symbol, OrderSide.SELL,
            new BigDecimal("3000.00"), new BigDecimal("10.0"));
        CommandResult sellResult = commandService.handleCommand(sellCommand);
        PlaceOrderResult sellOrderResult = (PlaceOrderResult) sellResult.getDate();

        assertEquals(0, sellOrderResult.getTrades().size()); // 无对手盘
        System.out.println("✅ 卖单挂单成功: " + sellOrderResult.getOrder());

        // 2. 下一个买单 @ 3000（价格匹配，应该成交）
        Command buyCommand = createPlaceOrderCommand("BUY-001", symbol, OrderSide.BUY,
            new BigDecimal("3000.00"), new BigDecimal("5.0"));
        CommandResult buyResult = commandService.handleCommand(buyCommand);
        PlaceOrderResult buyOrderResult = (PlaceOrderResult) buyResult.getDate();

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
        Command sellCommand = createPlaceOrderCommand("SELL-002", symbol, OrderSide.SELL,
            new BigDecimal("500.00"), new BigDecimal("100.0"));
        commandService.handleCommand(sellCommand);

        // 2. 买单 150 @ 500（只能成交100）
        Command buyCommand = createPlaceOrderCommand("BUY-002", symbol, OrderSide.BUY,
            new BigDecimal("500.00"), new BigDecimal("150.0"));
        CommandResult buyResult = commandService.handleCommand(buyCommand);
        PlaceOrderResult buyOrderResult = (PlaceOrderResult) buyResult.getDate();

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
        Command placeCommand = createPlaceOrderCommand("ORDER-CANCEL-001", symbol, OrderSide.BUY,
            new BigDecimal("1.50"), new BigDecimal("1000.0"));
        commandService.handleCommand(placeCommand);

        // 2. 撤单
        Command cancelCommand = createCancelOrderCommand("ORDER-CANCEL-001", symbol);
        CommandResult cancelResult = commandService.handleCommand(cancelCommand);
        CancelOrderResult cancelOrderResult = (CancelOrderResult) cancelResult.getDate();

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
            Command cmd = createPlaceOrderCommand("BUY-" + i, symbol, OrderSide.BUY,
                new BigDecimal(100 - i), new BigDecimal("10.0"));  // 99, 98, 97, 96, 95
            commandService.handleCommand(cmd);
        }

        // 添加多个卖单
        for (int i = 1; i <= 5; i++) {
            Command cmd = createPlaceOrderCommand("SELL-" + i, symbol, OrderSide.SELL,
                new BigDecimal(101 + i), new BigDecimal("10.0"));  // 102, 103, 104, 105, 106
            commandService.handleCommand(cmd);
        }

        // 查询订单薄
        Command queryCommand = createQueryOrderBookCommand(symbol, 5);
        CommandResult queryResult = commandService.handleCommand(queryCommand);

        OrderBookSnapshot snapshot = (OrderBookSnapshot) queryResult.getDate();

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
        commandService.handleCommand(createPlaceOrderCommand("SELL-A", symbol, OrderSide.SELL,
            new BigDecimal("0.10"), new BigDecimal("1000")));
        commandService.handleCommand(createPlaceOrderCommand("SELL-B", symbol, OrderSide.SELL,
            new BigDecimal("0.11"), new BigDecimal("2000")));
        commandService.handleCommand(createPlaceOrderCommand("SELL-C", symbol, OrderSide.SELL,
            new BigDecimal("0.12"), new BigDecimal("3000")));

        // 买方：市价吃单（价格设置很高，能吃掉所有卖单）
        Command bigBuyOrder = createPlaceOrderCommand("BUY-BIG", symbol, OrderSide.BUY,
            new BigDecimal("0.15"), new BigDecimal("5000"));  // 高于所有卖单价格，数量足够吃掉前两档

        CommandResult result = commandService.handleCommand(bigBuyOrder);
        PlaceOrderResult orderResult = (PlaceOrderResult) result.getDate();

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
        commandService.handleCommand(createPlaceOrderCommand("SELL-TIME-1", symbol, OrderSide.SELL,
            new BigDecimal("20.00"), new BigDecimal("10")));
        commandService.handleCommand(createPlaceOrderCommand("SELL-TIME-2", symbol, OrderSide.SELL,
            new BigDecimal("20.00"), new BigDecimal("10")));

        // 买单匹配
        Command buyCommand = createPlaceOrderCommand("BUY-TIME", symbol, OrderSide.BUY,
            new BigDecimal("20.00"), new BigDecimal("5"));
        CommandResult result = commandService.handleCommand(buyCommand);
        PlaceOrderResult orderResult = (PlaceOrderResult) result.getDate();

        // 应该只匹配第一个卖单（时间优先）
        assertEquals(1, orderResult.getTrades().size());
        assertEquals("SELL-TIME-1", orderResult.getTrades().get(0).getSellOrderId());

        System.out.println("✅ 价格-时间优先验证成功");
    }
}
