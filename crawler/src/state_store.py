import json
import os

from config import STATE_FILE_PATH


def load():
    if not os.path.exists(STATE_FILE_PATH):
        return {}
    with open(STATE_FILE_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save(state):
    os.makedirs(os.path.dirname(STATE_FILE_PATH), exist_ok=True)
    with open(STATE_FILE_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)
