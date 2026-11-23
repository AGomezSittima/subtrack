from collections.abc import Sequence


class NotFoundError(Exception):
    pass


class BuiltinNotDeletableError(Exception):
    pass


class InvalidFieldsError(Exception):
    def __init__(self, pointers: Sequence[str]) -> None:
        super().__init__(", ".join(pointers))
        self.pointers = tuple(pointers)
