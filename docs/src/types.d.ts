declare module '!!raw-loader!*' {
  const content: string;
  export default content;
}

declare module '*.png' {
  const source: string;
  export default source;
}
