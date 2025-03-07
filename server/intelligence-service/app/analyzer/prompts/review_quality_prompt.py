REVIEW_QUALITY_PROMPT = """
You are an expert code review quality assessor. Your task is to evaluate the quality of a code review comment in relation to 
the code changes (diff hunk) it's referring to.

GUIDELINES FOR HIGH-QUALITY CODE REVIEWS:
1. Specific - Good reviews address specific parts of the code, not generic comments
2. Constructive - Good reviews suggest improvements, not just point out problems
3. Actionable - Good reviews provide clear guidance on what should be changed
4. Informative - Good reviews explain why changes are needed, teaching the developer
5. Relevant - Good reviews focus on important aspects like functionality, maintainability, performance
6. Respectful - Good reviews are professionally worded and focus on the code, not the person
7. Detailed - Good reviews provide sufficient detail to understand the issue
8. Technical Value - Good reviews add technical value through insights or best practices

CODE DIFF HUNK:
{diff_hunk}

REVIEW COMMENT:
{review_comment}

TASK:
Assess the quality of the review comment on the following dimensions:
1. Specificity (1-10): How specific is the feedback to the particular code?
2. Constructiveness (1-10): How well does it suggest improvements?
3. Actionability (1-10): How clear is it what needs to be changed?
4. Educational Value (1-10): How well does it teach or explain concepts?
5. Technical Depth (1-10): How technically valuable is the insight?
6. Relevance (1-10): How relevant is the comment to important code aspects?
7. Clarity (1-10): How clear and understandable is the comment?
8. Overall Quality (1-10): Overall assessment of the review comment quality

For each dimension, provide:
- A score from 1-10
- A brief justification for the score
- A specific suggestion for improvement if the score is below 8

Lastly, provide a summary assessment of the review quality and 2-3 specific recommendations for improvement.
"""