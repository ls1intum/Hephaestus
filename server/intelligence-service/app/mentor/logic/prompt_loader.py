from pathlib import Path
from typing import Dict


class PromptLoader:
    def __init__(self, prompt_dir: str = "prompts"):
        self.prompt_dir = Path(__file__).parent / prompt_dir

    def load_prompts(self, type: str) -> Dict[str, str]:
        prompts = {}
        type_dir = self.prompt_dir / type
        for txt_file in type_dir.glob("*.txt"):
            key = txt_file.stem  # use the filename without extension as the key
            with open(txt_file, "r", encoding="utf-8") as f:
                prompts[key] = f.read().strip()
        return prompts

    def get_prompt(self, type: str, name: str) -> str:
        prompts = self.load_prompts(type)
        return prompts.get(name, "")
