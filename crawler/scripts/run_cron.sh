#!/usr/bin/env bash
# 테스트 서버 crontab 등록용 래퍼. 크론에 PATH/가상환경이 없어서 직접 지정한다.
#
# crontab -e 로 한 줄만 추가 (매일 04:00 KST). 파일로 덮어쓰면 자동 배포 줄이 지워진다:
#   0 4 * * * /srv/gongbang/crawler/crawler/scripts/run_cron.sh >> /srv/gongbang/crawler/crawler/cron.log 2>&1
# 서버의 /srv/gongbang/crawler 는 develop clone이다 (/srv/gongbang/app 은 배포 때마다 비워져서 쓰지 않는다).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CRAWLER_DIR="$(dirname "$SCRIPT_DIR")"

cd "$CRAWLER_DIR"
source .venv/bin/activate
python src/main.py
