from abc import ABC, abstractmethod
from typing import Type
from langchain_core.language_models.chat_models import BaseLanguageModel


class ModelProvider(ABC):
    """
    Abstract base class for model providers.

    This class defines the interface that any model provider must implement.
    """

    @abstractmethod
    def get_name(self) -> str:
        """
        Retrieve the name of the provider.

        Returns:
            str: The name of the provider.
        """
        pass

    @abstractmethod
    def validate_provider(self):
        """
        Validate the configuration and integrity of the provider.

        This method should perform any necessary checks to ensure that the provider is properly configured
        and ready to be used.

        Raises:
            ValueError: If the provider configuration is invalid.
        """
        pass

    @abstractmethod
    def validate_model_name(self, model_name: str):
        """
        Validate the provided model name for this provider, i.e. check if the model exists.

        Args:
            model_name (str): The model name to be validated.

        Raises:
            ValueError: If the model name is not valid for this provider.
        """
        pass

    @abstractmethod
    def get_model(self, model_name: str) -> Type[BaseLanguageModel]:
        """
        Retrieve a language model instance based on the given model name.

        Args:
            model_name (str): The name of the model to retrieve.

        Returns:
            BaseLanguageModel: An instance of a language model corresponding to the specified model name.
        """
        pass
