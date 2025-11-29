#!/bin/bash
#
# 低延迟 JVM 配置启动脚本
# 严格遵循 CLAUDE.md 中的低时延开发标准
#
# 使用方法:
#   ./scripts/run-low-latency.sh [gc_type]
#
# GC 类型:
#   g1      - G1GC (默认, 平衡性能)
#   zgc     - ZGC (超低延迟, 需要 Java 15+)
#   shenandoah - Shenandoah GC (低延迟)
#   epsilon - Epsilon GC (无 GC, 极短生命周期应用)
#

set -e

GC_TYPE="${1:-g1}"
APP_JAR="target/meta-driven-0.0.1-SNAPSHOT.jar"
CDS_ARCHIVE="app-cds.jsa"

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== 低延迟 JVM 启动脚本 ===${NC}"
echo -e "GC 类型: ${YELLOW}${GC_TYPE}${NC}"

# 检查 JAR 文件
if [ ! -f "$APP_JAR" ]; then
    echo -e "${RED}错误: 找不到应用 JAR 文件: ${APP_JAR}${NC}"
    exit 1
fi

# 基础 JVM 参数
BASE_OPTS=(
    # 内存配置 - 固定堆大小避免调整开销
    "-Xms4g"
    "-Xmx4g"

    # 堆预分配 - 避免运行时分配延迟
    "-XX:+AlwaysPreTouch"

    # 大页面支持 - 减少 TLB 未命中
    "-XX:+UseLargePages"
    "-XX:LargePageSizeInBytes=2m"

    # 禁用显式 GC
    "-XX:+DisableExplicitGC"

    # 优化字符串操作
    "-XX:+OptimizeStringConcat"

    # 压缩指针（4g 以下堆可用）
    "-XX:+UseCompressedOops"

    # 实时系统配置
    "-XX:+UnlockDiagnosticVMOptions"
    "-XX:GuaranteedSafepointInterval=300000"

    # 禁用偏向锁（现代 JVM 已弃用）
    "-XX:-UseBiasedLocking"

    # 编译器优化
    "-XX:+TieredCompilation"
    "-XX:TieredStopAtLevel=4"
    "-XX:CompileThreshold=1000"

    # 内联优化
    "-XX:MaxInlineLevel=15"
    "-XX:InlineSmallCode=2000"

    # CPU 优化
    "-XX:+UseStringDeduplication"
)

# 根据 GC 类型配置参数
case "$GC_TYPE" in
    g1)
        echo -e "${YELLOW}使用 G1GC (低延迟配置)${NC}"
        GC_OPTS=(
            "-XX:+UseG1GC"

            # 目标暂停时间 1ms
            "-XX:MaxGCPauseMillis=1"

            # G1 区域大小优化
            "-XX:G1HeapRegionSize=4m"

            # 并发标记线程数
            "-XX:ConcGCThreads=4"
            "-XX:ParallelGCThreads=8"

            # 减少 GC 开销
            "-XX:G1ReservePercent=10"
            "-XX:InitiatingHeapOccupancyPercent=45"

            # G1 混合 GC 优化
            "-XX:G1MixedGCCountTarget=8"
            "-XX:G1HeapWastePercent=5"

            # GC 日志
            "-Xlog:gc*:file=gc-g1.log:time,level,tags"
        )
        ;;

    zgc)
        echo -e "${YELLOW}使用 ZGC (超低延迟)${NC}"
        GC_OPTS=(
            "-XX:+UseZGC"

            # ZGC 代数模式（Java 21+）
            "-XX:+ZGenerational"

            # 并发 GC 线程
            "-XX:ConcGCThreads=4"

            # 大页面支持对 ZGC 很重要
            "-XX:+UseLargePages"
            "-XX:+UseTransparentHugePages"

            # GC 日志
            "-Xlog:gc*:file=gc-zgc.log:time,level,tags"
        )
        ;;

    shenandoah)
        echo -e "${YELLOW}使用 Shenandoah GC (低延迟)${NC}"
        GC_OPTS=(
            "-XX:+UseShenandoahGC"

            # Shenandoah 模式
            "-XX:ShenandoahGCMode=iu"

            # 并发线程配置
            "-XX:ConcGCThreads=4"
            "-XX:ParallelGCThreads=8"

            # GC 日志
            "-Xlog:gc*:file=gc-shenandoah.log:time,level,tags"
        )
        ;;

    epsilon)
        echo -e "${YELLOW}使用 Epsilon GC (无 GC 操作)${NC}"
        echo -e "${RED}警告: Epsilon GC 不执行垃圾回收, 仅适用于极短生命周期应用${NC}"
        GC_OPTS=(
            "-XX:+UnlockExperimentalVMOptions"
            "-XX:+UseEpsilonGC"

            # 更大的堆以容纳所有分配
            "-Xms8g"
            "-Xmx8g"
        )
        ;;

    *)
        echo -e "${RED}错误: 未知 GC 类型 '${GC_TYPE}'${NC}"
        echo -e "支持的类型: g1, zgc, shenandoah, epsilon"
        exit 1
        ;;
esac

# CDS 归档（如果存在）
if [ -f "$CDS_ARCHIVE" ]; then
    echo -e "${GREEN}使用 CDS 归档加速启动${NC}"
    CDS_OPTS=(
        "-Xshare:on"
        "-XX:SharedArchiveFile=${CDS_ARCHIVE}"
    )
else
    echo -e "${YELLOW}未找到 CDS 归档, 使用标准启动${NC}"
    CDS_OPTS=()
fi

# 性能监控选项（可选）
MONITORING_OPTS=(
    # JMX 监控
    "-Dcom.sun.management.jmxremote"
    "-Dcom.sun.management.jmxremote.port=9010"
    "-Dcom.sun.management.jmxremote.authenticate=false"
    "-Dcom.sun.management.jmxremote.ssl=false"

    # Java Flight Recorder
    "-XX:+FlightRecorder"
)

# 组合所有 JVM 参数
ALL_OPTS=(
    "${BASE_OPTS[@]}"
    "${GC_OPTS[@]}"
    "${CDS_OPTS[@]}"
    # "${MONITORING_OPTS[@]}"  # 生产环境可启用
)

# 启动应用
echo -e "${GREEN}启动低延迟应用...${NC}"
echo -e "JVM 参数:"
printf '%s\n' "${ALL_OPTS[@]}"
echo ""

exec java "${ALL_OPTS[@]}" -jar "$APP_JAR"