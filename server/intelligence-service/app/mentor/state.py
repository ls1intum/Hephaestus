from langgraph.graph import MessagesState


class MentorState(MessagesState):
    """State for the mentor graph."""

    user_id: int = None
