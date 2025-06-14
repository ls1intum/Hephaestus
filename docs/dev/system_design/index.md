# System Design

## Overview

Hephaestus is designed using an architecture-first approach. To develop and visualize our system design, we use [StarUML](https://staruml.io/), a professional UML modeling software that allows for comprehensive representation of system components, relationships, and behaviors.
The source file for all models and diagrams displayed in this documentation can be found on Github and [here](./hephaestus.mdj).

## Top-Level Design

The top-level architecture diagram illustrates Hephaestus as a layered system with clear boundaries between components. 
The diagram shows the system divided into Hephaestus-internal and external components.

Internally, Hephaestus is based on a client-server architecture with multiple server components. 
We differentiate between the _Application Server_ and the _Intelligence Service_ as well as the smaller _Webhook Ingest_ and the _User Management System_.

Externally, Hephaestus is connected to the _Code Review System_, the _Notification System_, and the _Analytics Platform_. 
The _Code Review System_ is an abstraction for the actual Git hosting platform, e.g. GitHub or GitLab. 
The _Webhook Ingest_ provides an endpoint this platform can send webhooks to. 
The _Application Server_ on the other hand utilizes the platform's API to supplement the webhook data with additional information or fill in missing data. 
The _User Management System_ utilizes the _Code Review System_ as identity provider to create a direct dependency for the user login. 
The _Notification System_ bridges the gap between the client and server to send notifications to users. 
The _Analytics Platform_ is used to store and analyze data, generally limited to anonymous usage metrics of the web application.

```{figure} ./top_level_architecture.svg
:alt: Hephaestus Top Level Architecture

Hephaestus Top Level Architecture (UML Component Diagram)
```

## Gamification Model

To implement gamification features, we built upon an adapted basic structure by the Github's webhooks. 
The analysis object model presents the data structure of Hephaestus through class relationships. 
Note that the model is simplified and some relationships are not shown for brevity. 

+Github's webhook structure is more or less directly mapped to the _Review_, _PullRequest_ and _Developer_ classes. 
The original events contain lots of irrelevant and duplicate information, which is why we added certain relationships to the classes to reduce redundancy.

Around these core classes, we added additional classes to model the gamification features. 
Most importantly, we introduced the _Leaderboard_ and _Reward_ abstractions to model the leaderboard and rewards system, respectively.
All gamification related data is managed by the _GamificationDashboard_.

```{figure} ./gamification_analysis_object_model.svg
:alt: Gamification Analysis Object Model

Gamification Analysis Object Model (UML Class Diagram)
```

## AI Mentor

To implement AI mentor features for self-reflective practices we utilize LangGraph, a library built by LangChain that allows users to create complex, stateful, multi-agent applications using graph-based architectures. 

The system maintains a state, which represents a current snapshot of the application. Each call to the AI Mentor triggers a run of the graph, which consists of nodes and edges. Nodes receive the current state as input, perform specific operations — such as updating state parameters or generating responses — and return an updated state. Edges determine the next node to execute based on the current state, functioning as either conditional branches or fixed transitions.

There are four main elements of the graph:
- Storage update nodes: Functions that update data saved in storage, maintaining long-term memory across all interactions with the AI Mentor.
- Response generation nodes: Functions responsible for generating responses that users receive in the chat interface.
- State update nodes: Functions that update the current state of the graph (a TypedDict containing information about the current chat).
- Conditions, or conditional edges: Edges that evaluate the current state and determine which node to execute next.

```{figure} ./langgraph.png
:alt: LangGraph Graph Structure Used for Each of the Response Generation Calls
:width: 850px
LangGraph Graph Structure Used for Each of the Response Generation Calls
```

## User Interaction

As web applications are highly interactive, we want to model the basic interactions between the user and the system, giving a high-level overview of the possible user journeys. 
For that reason, we focus on a _Developer_ as the primary actor of the system and model their interactions with the system.

```{figure} ./gamification_use_cases.svg
:width: 700px
:alt: Hephaestus Gamification Use Case Diagram

Hephaestus Gamification Use Cases (UML Use Case Diagram)
```

```{figure} ./mentor_use_cases.svg
:width: 850px
:alt: Hephaestus AI Mentor Use Case Diagram

Hephaestus AI Mentor Use Cases (UML Use Case Diagram)
```

## Core Workflows

The following sections describe the core workflows of Hephaestus in more detail.

### Login and Authorization

The login and authorization workflow is the first step in the user journey. 
It is used to authenticate the user and authorize the application to access their resources. 
Primarily, the user can log in via the _Code Review System_ (e.g. GitHub or GitLab).

```{figure} ./login_authorization_flow.svg
:alt: Hephaestus Github Login Authorization Flow

Hephaestus Github Login Authorization Flow (UML Sequence Diagram)
```

### New Review Activity

The new review activity is the fundamental workflow allowing Hephaestus to provide real-time data to the user. 
It is triggered when a new review is created and updates the leaderboard (and in the future the user's rewards) based on the new activity. 
This flow heavily relies on the _Webhook Ingest_ to receive the webhook events from the _Code Review System_ and the _Application Server_ to process them accordingly.

```{figure} ./new_review_activity.svg
:alt: Hephaestus New Review Activity

Hephaestus New Review Activity (UML Activity Diagram)
```