import datetime
import json
import os

from config import OUTPUT_DIR


def save(contests):
    """백엔드가 준비되기 전까지, 수집 결과를 JSON 파일로 저장해 확인한다."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    filename = datetime.datetime.now().strftime("%Y%m%d_%H%M%S") + ".json"
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(contests, f, ensure_ascii=False, indent=2)
    return path
