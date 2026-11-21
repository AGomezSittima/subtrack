const todoWithoutIssue = /\bTODO\b(?!\(#\d+\))/u;

const todoNeedsIssue = {
  meta: {
    type: "suggestion",
    docs: { description: "Require TODO comments to link a GitHub issue" },
    schema: [],
    messages: { missingIssue: "Write TODO(#<issue>): link a GitHub issue." },
  },
  create(context) {
    return {
      Program() {
        for (const comment of context.sourceCode.getAllComments()) {
          if (todoWithoutIssue.test(comment.value)) {
            context.report({ loc: comment.loc, messageId: "missingIssue" });
          }
        }
      },
    };
  },
};

export const subtrack = {
  meta: { name: "eslint-plugin-subtrack" },
  rules: { "todo-needs-issue": todoNeedsIssue },
};

export const commentRules = {
  "no-warning-comments": [
    "error",
    { terms: ["fixme", "xxx", "hack"], location: "anywhere" },
  ],
  "subtrack/todo-needs-issue": "error",
};
