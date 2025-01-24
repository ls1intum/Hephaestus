from psycopg_pool import ConnectionPool
from langgraph.graph import START, StateGraph, END
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.store.postgres import PostgresStore
from langchain_core.messages import HumanMessage

from .state import State
from ..settings import settings

from .nodes import (
    greet,
    ask_impediments,
    ask_status,
    ask_promises,
    ask_summary,
    check_state,
    finish,
    update_memory,
    talk_to_mentor,
)
from .conditions import start_router, main_router

connection_kwargs = {
    "autocommit": True,
    "prepare_threshold": 0,
}

graph_builder = StateGraph(State)
graph_builder.add_node("greeting", greet)
graph_builder.add_node("status_node", ask_status)
graph_builder.add_node("impediments_node", ask_impediments)
graph_builder.add_node("promises_node", ask_promises)
graph_builder.add_node("summary_node", ask_summary)
graph_builder.add_node("check_state", check_state)
graph_builder.add_node("finish_node", finish)
graph_builder.add_node("update_memory", update_memory)
graph_builder.add_node("mentor_node", talk_to_mentor)

graph_builder.add_conditional_edges(START, start_router)
graph_builder.add_conditional_edges("check_state", main_router)

graph_builder.add_edge("greeting", END)
graph_builder.add_edge("status_node", END)
graph_builder.add_edge("impediments_node", END)
graph_builder.add_edge("promises_node", END)
graph_builder.add_edge("summary_node", END)
graph_builder.add_edge("finish_node", "update_memory")
graph_builder.add_edge("update_memory", END)
graph_builder.add_edge("mentor_node", END)


def start_session(last_thread: str, config):
    with ConnectionPool(
        conninfo=settings.DATABASE_CONNECTION_STRING,
        max_size=20,
        kwargs=connection_kwargs,
    ) as pool:
        checkpointer = PostgresSaver(pool)
        checkpointer.setup()

        with PostgresStore.from_conn_string(
            settings.DATABASE_CONNECTION_STRING
        ) as store:
            store.setup()
            graph = graph_builder.compile(checkpointer=checkpointer, store=store)

            # set the initial state of the graph
            result = graph.invoke(
                {
                    "last_thread": last_thread,
                    "messages": [],
                    "impediments": False,
                    "status": False,
                    "promises": False,
                    "summary": False,
                    "finish": False,
                    "closed": False,
                },
                config,
            )

        pool.close()
        return result


def run(message: str, config):
    with ConnectionPool(
        conninfo=settings.DATABASE_CONNECTION_STRING,
        max_size=20,
        kwargs=connection_kwargs,
    ) as pool:
        checkpointer = PostgresSaver(pool)
        checkpointer.setup()

        with PostgresStore.from_conn_string(
            settings.DATABASE_CONNECTION_STRING
        ) as store:
            store.setup()
            graph = graph_builder.compile(checkpointer=checkpointer, store=store)
            # update the state with the new message (the rest of the state is preserved by the checkpointer)
            result = graph.invoke({"messages": [HumanMessage(content=message)]}, config)

        pool.close()
        return result
