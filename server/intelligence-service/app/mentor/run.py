from psycopg_pool import ConnectionPool
from langgraph.graph import START, StateGraph, END
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.store.postgres import PostgresStore
from langchain_core.messages import HumanMessage

from app.mentor.state import State
from app.settings import settings

from .conditions import (
    start_router,
    main_router,
    goal_reflection_router,
    goal_setting_router,
)
from .nodes.memory_updates import update_memory, set_goals, adjust_goals
from .nodes.state_updates import check_state, check_goals, check_goal_reflection
from .nodes.mentor_interaction import (
    greet,
    get_dev_progress,
    ask_status,
    ask_impediments,
    ask_promises,
    ask_summary,
    finish,
    ask_goals,
    reflect_goals,
)

connection_kwargs = {
    "autocommit": True,
    "prepare_threshold": 0,
}

graph_builder = StateGraph(State)

# mentor interaction nodes: generate direct output for the user
graph_builder.add_node("greeting", greet)
graph_builder.add_node("development_node", get_dev_progress)
graph_builder.add_node("status_node", ask_status)
graph_builder.add_node("impediments_node", ask_impediments)
graph_builder.add_node("promises_node", ask_promises)
graph_builder.add_node("summary_node", ask_summary)
graph_builder.add_node("finish_node", finish)
graph_builder.add_node("goal_setting_node", ask_goals)
graph_builder.add_node("goal_reflection_node", reflect_goals)

# chat analysis nodes: update the state of the conversation
graph_builder.add_node("check_state", check_state)
graph_builder.add_node("check_goals", check_goals)
graph_builder.add_node("check_goal_reflection", check_goal_reflection)

# memory nodes: update the long-term memory of the mentor
graph_builder.add_node("update_memory", update_memory)
graph_builder.add_node("set_goals", set_goals)
graph_builder.add_node("adjust_goals", adjust_goals)

graph_builder.add_conditional_edges(START, start_router)
graph_builder.add_conditional_edges("check_state", main_router)
graph_builder.add_conditional_edges("check_goals", goal_setting_router)
graph_builder.add_conditional_edges("check_goal_reflection", goal_reflection_router)
graph_builder.add_conditional_edges("set_goals", main_router)
graph_builder.add_conditional_edges("adjust_goals", main_router)

graph_builder.add_edge("greeting", END)
graph_builder.add_edge("development_node", END)
graph_builder.add_edge("status_node", END)
graph_builder.add_edge("impediments_node", END)
graph_builder.add_edge("promises_node", END)
graph_builder.add_edge("summary_node", END)
graph_builder.add_edge("update_memory", END)
graph_builder.add_edge("goal_setting_node", END)
graph_builder.add_edge("goal_reflection_node", END)
graph_builder.add_edge("finish_node", "update_memory")


def start_session(last_thread: str, user_id: str, dev_progress: str, config):
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
                    "user_id": user_id,
                    "last_thread": last_thread,
                    "dev_progress": dev_progress,
                    "messages": [],
                    "goal_setting": False,
                    "development": False,
                    "status": False,
                    "impediments": False,
                    "promises": False,
                    "summary": False,
                    "goal_reflection": False,
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
