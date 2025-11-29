#!/bin/bash
#
# CDS (Class Data Sharing) 归档生成脚本
# 用于生成类数据共享归档，加速应用启动
#
# 使用方法:
#   ./scripts/generate-cds.sh
#

set -e

APP_JAR="target/meta-driven-0.0.1-SNAPSHOT.jar"
CDS_ARCHIVE="app-cds.jsa"
CLASS_LIST="classlist.txt"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== CDS 归档生成脚本 ===${NC}"

# 检查 JAR 文件
if [ ! -f "$APP_JAR" ]; then
    echo -e "${RED}错误: 找不到应用 JAR 文件: ${APP_JAR}${NC}"
    echo -e "${YELLOW}请先运行: ./mvnw clean package${NC}"
    exit 1
fi

# 第一步: 生成类列表
echo -e "${YELLOW}步骤 1/3: 生成加载类列表...${NC}"
java -Xshare:off \
     -XX:DumpLoadedClassList=${CLASS_LIST} \
     -jar ${APP_JAR} &

APP_PID=$!
echo "应用 PID: ${APP_PID}"

# 等待应用启动并加载类
echo "等待 10 秒以加载所有类..."
sleep 10

# 停止应用
echo "停止应用..."
kill ${APP_PID} 2>/dev/null || true
wait ${APP_PID} 2>/dev/null || true

if [ ! -f "${CLASS_LIST}" ]; then
    echo -e "${RED}错误: 类列表文件未生成${NC}"
    exit 1
fi

echo -e "${GREEN}类列表已生成: ${CLASS_LIST}${NC}"
CLASS_COUNT=$(wc -l < ${CLASS_LIST})
echo "加载的类数量: ${CLASS_COUNT}"

# 第二步: 创建 CDS 归档
echo -e "${YELLOW}步骤 2/3: 创建 CDS 归档...${NC}"
java -Xshare:dump \
     -XX:SharedClassListFile=${CLASS_LIST} \
     -XX:SharedArchiveFile=${CDS_ARCHIVE} \
     -jar ${APP_JAR}

if [ ! -f "${CDS_ARCHIVE}" ]; then
    echo -e "${RED}错误: CDS 归档文件未生成${NC}"
    exit 1
fi

echo -e "${GREEN}CDS 归档已生成: ${CDS_ARCHIVE}${NC}"
ARCHIVE_SIZE=$(du -h ${CDS_ARCHIVE} | cut -f1)
echo "归档大小: ${ARCHIVE_SIZE}"

# 第三步: 验证 CDS 归档
echo -e "${YELLOW}步骤 3/3: 验证 CDS 归档...${NC}"
java -Xshare:on \
     -XX:SharedArchiveFile=${CDS_ARCHIVE} \
     -version

if [ $? -eq 0 ]; then
    echo -e "${GREEN}CDS 归档验证成功!${NC}"
else
    echo -e "${RED}CDS 归档验证失败${NC}"
    exit 1
fi

# 清理临时文件
echo -e "${YELLOW}清理临时文件...${NC}"
rm -f ${CLASS_LIST}

echo -e "${GREEN}=== CDS 归档生成完成 ===${NC}"
echo -e "生成的文件: ${CDS_ARCHIVE}"
echo -e "使用方法: java -Xshare:on -XX:SharedArchiveFile=${CDS_ARCHIVE} -jar ${APP_JAR}"