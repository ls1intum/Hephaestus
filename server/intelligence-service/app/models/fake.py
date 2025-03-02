from langchain_core.language_models.fake_chat_models import FakeChatModel

from app.models.model_provider import ModelProvider


class FakeProvider(ModelProvider):
    def get_name(self) -> str:
        return "fake"

    def validate_provider(self):
        pass

    def validate_model_name(self, model_name: str):
        pass

    def get_model(self, model_name: str):
        class ChatModel(FakeChatModel):
            pass

        return ChatModel


fake_provider = FakeProvider()
