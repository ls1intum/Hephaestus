This section contains guidelines for working with tools of the Hephaestus Intelligence Service.

### AI Mentor

#### Folder and File Structure

The logic of the AI mentor can be found in `server/intelligence_service/app/mentor`. This folder contains the following documents and folders: 
- **run.py**: Contains the main entry point for the AI mentor and defines the LangGraph structure.
- **state.py**: Defines the TypedDict classes that represent the graph state during execution.
- **prompt_loader.py**: Utility for loading prompt templates from files with proper formatting.
- **conditions.py**: Contains functions that evaluate the graph state and determine execution paths.
- **nodes**: Directory containing all node functions (storage update, response generation, and state update).
- **prompts**: Directory containing prompt templates organized by purpose.
    - **/analyzer**: Prompts used for analyzing user responses, these are used for the internal logic, not user response generation.
    - **/mentor**: Prompts used for generating mentorship responses, sent to the user.


