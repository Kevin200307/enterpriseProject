from dataclasses import dataclass


class BudgetExceeded(RuntimeError):
    pass


@dataclass(slots=True)
class TurnBudget:
    max_model_calls: int = 2
    max_tool_calls: int = 3
    model_calls: int = 0
    tool_calls: int = 0

    def use_model(self) -> None:
        if self.model_calls >= self.max_model_calls:
            raise BudgetExceeded("Model-call budget exceeded")
        self.model_calls += 1

    def use_tool(self) -> None:
        if self.tool_calls >= self.max_tool_calls:
            raise BudgetExceeded("Tool-call budget exceeded")
        self.tool_calls += 1
