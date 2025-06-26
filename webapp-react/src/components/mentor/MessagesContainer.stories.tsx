import type { Meta, StoryObj } from "@storybook/react";
import { MessagesContainer } from "./MessagesContainer";

/**
 * Container component for displaying a list of chat messages with auto-scroll behavior.
 * Handles empty states and manages message rendering.
 */
const meta = {
	component: MessagesContainer,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		isStreaming: {
			control: "boolean",
			description: "Shows streaming indicator on the last message",
		},
		streamingMessageId: {
			control: "text",
			description: "ID of the message currently being streamed",
		},
	},
	decorators: [
		(Story) => (
			<div className="h-96 w-96 border rounded-lg">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof MessagesContainer>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty state when no messages have been sent yet.
 */
export const Empty: Story = {
	args: {
		messages: [],
	},
};

/**
 * Single exchange between user and assistant.
 */
export const SingleExchange: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [{ type: "text", text: "What is React?" }],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "React is a JavaScript library for building user interfaces, particularly web applications. It was created by Facebook and is now maintained by Meta and the open-source community.",
					},
				],
			},
		],
	},
};

/**
 * Multiple message conversation.
 */
export const Conversation: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [{ type: "text", text: "Can you explain React hooks?" }],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "React hooks are functions that let you use state and lifecycle features in functional components. The most common ones are useState and useEffect.",
					},
				],
			},
			{
				id: "3",
				role: "user",
				parts: [
					{ type: "text", text: "Can you show me an example of useState?" },
				],
			},
			{
				id: "4",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Here's a simple useState example:\n\n```jsx\nconst [count, setCount] = useState(0);\n\nreturn (\n  <button onClick={() => setCount(count + 1)}>\n    Count: {count}\n  </button>\n);\n```",
					},
				],
			},
			{
				id: "5",
				role: "user",
				parts: [
					{ type: "text", text: "That's helpful! What about useEffect?" },
				],
			},
		],
	},
};

/**
 * Conversation with streaming message being typed.
 */
export const WithStreaming: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [{ type: "text", text: "Explain useEffect with an example" }],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "useEffect is a hook that lets you perform side effects in functional components. Here's an example that fetches data:\n\n```jsx\nuseEffect(() => {\n  fetch('/api/data')\n    .then(response => response.json())\n    .then(data => setData(data));\n}, []);\n```\n\nThe empty dependency array means this effect runs only once after the component mounts",
					},
				],
			},
		],
		isStreaming: true,
		streamingMessageId: "2",
	},
};

/**
 * Long conversation that requires scrolling.
 */
export const LongConversation: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "What are the main React concepts I should learn?",
					},
				],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Here are the main React concepts you should learn:\n\n1. Components and JSX\n2. Props and State\n3. Event Handling\n4. Conditional Rendering\n5. Lists and Keys\n6. Forms\n7. Lifting State Up\n8. Composition vs Inheritance",
					},
				],
			},
			{
				id: "3",
				role: "user",
				parts: [
					{ type: "text", text: "Can you explain components in more detail?" },
				],
			},
			{
				id: "4",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Components are the building blocks of React applications. They let you split the UI into independent, reusable pieces. There are two main types:\n\n**Function Components:**\n```jsx\nfunction Welcome(props) {\n  return <h1>Hello, {props.name}!</h1>;\n}\n```\n\n**Class Components:**\n```jsx\nclass Welcome extends React.Component {\n  render() {\n    return <h1>Hello, {this.props.name}!</h1>;\n  }\n}\n```\n\nFunction components are preferred in modern React development.",
					},
				],
			},
			{
				id: "5",
				role: "user",
				parts: [{ type: "text", text: "What about props vs state?" }],
			},
			{
				id: "6",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "**Props** are read-only inputs passed to components:\n- Data flows down from parent to child\n- Cannot be modified by the receiving component\n- Used to configure and customize components\n\n**State** is mutable data owned by a component:\n- Can be changed using setState (class) or useState (hooks)\n- Triggers re-renders when updated\n- Local to the component\n\nThink of props as function parameters and state as local variables.",
					},
				],
			},
			{
				id: "7",
				role: "user",
				parts: [{ type: "text", text: "This is really helpful, thank you!" }],
			},
			{
				id: "8",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "You're welcome! I'm glad I could help explain these React concepts. Remember that practice is key - try building small components and gradually work your way up to more complex applications. Feel free to ask if you have any more questions!",
					},
				],
			},
		],
	},
};
