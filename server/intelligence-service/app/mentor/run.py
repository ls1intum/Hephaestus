from .state import State
from langgraph.graph import START, StateGraph, END
from psycopg_pool import ConnectionPool
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.store.postgres import PostgresStore
from langchain_core.messages import HumanMessage

from .nodes import (
    greet,
    ask_impediments,
    ask_status,
    ask_promises,
    ask_summary,
    check_state,
    finish,
    update_memory,
    get_dev_progress,
)
from .conditions import start_router, main_router


POSTGRES_CONFIG = {
    "dbname": "hephaestus",
    "user": "root",
    "password": "root",
    "host": "localhost",
    "port": "5432",
    "autocommit": True,
    "prepare_threshold": 0,
}

graph_builder = StateGraph(State)
graph_builder.add_node("greeting", greet)
graph_builder.add_node("development_node", get_dev_progress)
graph_builder.add_node("status_node", ask_status)
graph_builder.add_node("impediments_node", ask_impediments)
graph_builder.add_node("promises_node", ask_promises)
graph_builder.add_node("summary_node", ask_summary)
graph_builder.add_node("check_state", check_state)
graph_builder.add_node("finish_node", finish)
graph_builder.add_node("update_memory", update_memory)

graph_builder.add_conditional_edges(START, start_router)
graph_builder.add_conditional_edges("check_state", main_router)

graph_builder.add_edge("greeting", END)
graph_builder.add_edge("development_node", END)
graph_builder.add_edge("status_node", END)
graph_builder.add_edge("impediments_node", END)
graph_builder.add_edge("promises_node", END)
graph_builder.add_edge("summary_node", END)
graph_builder.add_edge("finish_node", "update_memory")
graph_builder.add_edge("update_memory", END)


def start_session(last_thread: str, dev_progress: str, config):
    with ConnectionPool(kwargs=POSTGRES_CONFIG) as pool:
        checkpointer = PostgresSaver(pool)
        checkpointer.setup()

        with PostgresStore.from_conn_string(
            "postgresql://root:root@localhost:5432/hephaestus"
        ) as store:
            store.setup()
            graph = graph_builder.compile(checkpointer=checkpointer, store=store)

            # set the initial state of the graph
            result = graph.invoke(
                {
                    "last_thread": last_thread,
                    "dev_progress": dev_progress,
                    "messages": [],
                    "development": False,
                    "status": False,
                    "impediments": False,
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
    with ConnectionPool(kwargs=POSTGRES_CONFIG) as pool:
        checkpointer = PostgresSaver(pool)
        checkpointer.setup()

        with PostgresStore.from_conn_string(
            "postgresql://root:root@localhost:5432/hephaestus"
        ) as store:
            store.setup()
            graph = graph_builder.compile(checkpointer=checkpointer, store=store)
            # update the state with the new message (the rest of the state is preserved by the checkpointer)
            result = graph.invoke({"messages": [HumanMessage(content=message)]}, config)

        pool.close()
        return result
