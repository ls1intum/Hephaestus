import uuid
from typing import Union

# Default namespace used to scope deterministic IDs
DEFAULT_NAMESPACE_UUID = uuid.UUID("11111111-2222-3333-4444-555555555555")


def tool_call_id_to_uuid(
    call_id: str,
    namespace: Union[uuid.UUID, str] = DEFAULT_NAMESPACE_UUID,
    *,
    as_str: bool = True,
) -> Union[str, uuid.UUID]:
    """
    Convert a tool call id into a deterministic UUIDv5 within the given namespace.

    Args:
        call_id: The tool call identifier (must be a non-empty string).
        namespace: The namespace UUID (uuid.UUID or UUID string). Defaults to DEFAULT_NAMESPACE_UUID.
        as_str: If True, return a string; otherwise return a uuid.UUID object.

    Returns:
        A deterministic UUID (string or uuid.UUID) derived from (namespace, call_id).

    Raises:
        ValueError: If call_id is empty/invalid or namespace cannot be parsed as a UUID.

    Example:
        >>> tool_call_id_to_uuid("call_4ittk4cdDh9sz8Cyi1UQPsBM")
        '66f10bcf-a4de-568b-a497-022e9773a65b'
    """
    if not call_id or not isinstance(call_id, str):
        raise ValueError("call_id must be a non-empty string")

    ns = namespace if isinstance(namespace, uuid.UUID) else uuid.UUID(str(namespace))
    doc_id = uuid.uuid5(ns, call_id)
    return str(doc_id) if as_str else doc_id
