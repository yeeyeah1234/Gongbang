#!/usr/bin/env bash
# 테스트 서버 crontab 등록용 래퍼. 크론에 PATH/가상환경이 없어서 직접 지정한다.
#
# crontab -e 에 추가 (매일 04:00):
#   0 4 * * * /home/gongbang/Gongbang/crawler/scripts/run_cron.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CRAWLER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$CRAWLER_DIR"
source .venv/bin/activate
python src/main.py
