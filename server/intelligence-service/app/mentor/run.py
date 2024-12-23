from .state import State
from langgraph.graph import START, StateGraph, END
from psycopg_pool import AsyncConnectionPool
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langchain_core.messages import HumanMessage

from .nodes import greeting, mentor, ask_impediments, ask_status, ask_promises, ask_summary, check_state
from .conditions import start_router, router

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
graph_builder.add_node("greeting", greeting)
graph_builder.add_node("status_node", ask_status)
graph_builder.add_node("impediments_node", ask_impediments)
graph_builder.add_node("promises_node", ask_promises)
graph_builder.add_node("summary_node", ask_summary)
graph_builder.add_node("mentor_node", mentor)
graph_builder.add_node("check_state", check_state)

graph_builder.add_conditional_edges(START, start_router)
graph_builder.add_edge("greeting", END)

graph_builder.add_conditional_edges("check_state", router)

graph_builder.add_edge("mentor_node", END)
graph_builder.add_edge("status_node", END)
graph_builder.add_edge("impediments_node", END)
graph_builder.add_edge("promises_node", END)
graph_builder.add_edge("summary_node", END)


async def run(message: str, config):
    async with AsyncConnectionPool(kwargs=POSTGRES_CONFIG) as pool:
        checkpointer = AsyncPostgresSaver(pool)
        await checkpointer.setup()

        graph = graph_builder.compile(checkpointer=checkpointer)

        result = await graph.ainvoke(
            {"messages": [HumanMessage(content=message)]}, config
        )
        
        await pool.close()
        return result


