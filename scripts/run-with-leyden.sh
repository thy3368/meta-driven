#!/bin/bash
#
# Spring Boot 4 + Project Leyden 启动脚本
# 用于超低延迟应用启动和运行
#
# 使用方法:
#   ./scripts/run-with-leyden.sh [training|production]
#
# 模式说明:
#   training    - 训练运行模式，生成 CDS 归档和 AOT 优化数据
#   production  - 生产运行模式，使用预生成的优化数据

set -e

MODE="${1:-production}"
APP_JAR="target/meta-driven-0.0.1-SNAPSHOT.jar"
CDS_ARCHIVE="app-cds.jsa"
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Spring Boot 4 + Project Leyden 启动脚本 ===${NC}"
echo -e "模式: ${YELLOW}${MODE}${NC}"
echo -e "Java 版本: ${YELLOW}${JAVA_VERSION}${NC}"

# 检查 Java 版本
if [ "$JAVA_VERSION" -lt 22 ]; then
    echo -e "${RED}错误: Project Leyden 需要 Java 22 或更高版本${NC}"
    echo -e "${RED}当前版本: ${JAVA_VERSION}${NC}"
    exit 1
fi

# 检查 JAR 文件
if [ ! -f "$APP_JAR" ]; then
    echo -e "${RED}错误: 找不到应用 JAR 文件: ${APP_JAR}${NC}"
    echo -e "${YELLOW}请先运行: ./mvnw clean package -Pleyden-aot${NC}"
    exit 1
fi

# 基础 JVM 参数 - 符合 CLAUDE.md 低延迟标准
BASE_JVM_OPTS=(
    # 内存配置
    "-Xms2g"
    "-Xmx2g"

    # GC 配置 - 使用 G1GC 或 ZGC 用于低延迟
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=1"
    "-XX:+AlwaysPreTouch"

    # 大页面支持
    "-XX:+UseLargePages"
    "-XX:LargePageSizeInBytes=2m"

    # 优化配置
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+OptimizeStringConcat"
    "-XX:+UseCompressedOops"

    # 禁用显式 GC
    "-XX:+DisableExplicitGC"

    # 实时系统配置
    "-XX:+UnlockDiagnosticVMOptions"
    "-XX:GuaranteedSafepointInterval=300000"

    # Project Leyden 基础配置
    "-XX:+UnlockExperimentalVMOptions"

    # Spring Boot 配置
    "-Dspring.aot.enabled=true"
    "-Dspring.native.remove-unused-autoconfig=true"
)

# 根据模式选择不同的启动参数
case "$MODE" in
    training)
        echo -e "${YELLOW}=== 训练运行模式 ===${NC}"
        echo -e "将生成 CDS 归档: ${CDS_ARCHIVE}"

        TRAINING_OPTS=(
            "${BASE_JVM_OPTS[@]}"
            # CDS 训练模式 - 生成归档
            "-Xshare:off"
            "-XX:ArchiveClassesAtExit=${CDS_ARCHIVE}"
            "-XX:+RecordDynamicDumpInfo"

            # AOT 编译预热
            "-XX:CompileThreshold=100"
            "-XX:+PrintCompilation"

            # 训练数据收集
            "-XX:+FlightRecorder"
            "-XX:StartFlightRecording=duration=60s,filename=training-profile.jfr"
        )

        echo -e "${GREEN}启动训练运行...${NC}"
        java "${TRAINING_OPTS[@]}" -jar "$APP_JAR"

        echo -e "${GREEN}训练完成!${NC}"
        echo -e "生成的文件:"
        echo -e "  - ${CDS_ARCHIVE} (CDS 归档)"
        echo -e "  - training-profile.jfr (性能分析数据)"
        ;;

    production)
        echo -e "${YELLOW}=== 生产运行模式 ===${NC}"

        # 检查 CDS 归档
        if [ ! -f "$CDS_ARCHIVE" ]; then
            echo -e "${YELLOW}警告: 未找到 CDS 归档文件${NC}"
            echo -e "${YELLOW}建议先运行训练模式: ./scripts/run-with-leyden.sh training${NC}"
            echo -e "${YELLOW}继续使用标准模式启动...${NC}"

            PRODUCTION_OPTS=(
                "${BASE_JVM_OPTS[@]}"
            )
        else
            echo -e "${GREEN}使用 CDS 归档: ${CDS_ARCHIVE}${NC}"

            PRODUCTION_OPTS=(
                "${BASE_JVM_OPTS[@]}"
                # 使用 CDS 归档
                "-Xshare:on"
                "-XX:SharedArchiveFile=${CDS_ARCHIVE}"

                # 生产环境优化
                "-XX:+TieredCompilation"
                "-XX:TieredStopAtLevel=4"

                # 减少编译时间
                "-XX:CICompilerCount=4"
            )
        fi

        echo -e "${GREEN}启动生产应用...${NC}"
        java "${PRODUCTION_OPTS[@]}" -jar "$APP_JAR"
        ;;

    *)
        echo -e "${RED}错误: 未知模式 '${MODE}'${NC}"
        echo -e "支持的模式: training, production"
        exit 1
        ;;
esac