import base64
import json

from android.os import Looper
from org.telegram.messenger.plugins import PluginStorage

from fluxgram.storage import (
    StorageAccountUnavailableError,
    StorageError,
    StorageInactiveError,
    StorageLimits,
    StorageOwnerChangedError,
    StorageQuotaExceededError,
    StorageStatistics,
    StorageThreadError,
    StorageTransactionError,
    _StoredValue,
)

from ._ui_thread import post_ui


class AndroidStoragePort:
    def __init__(self, plugin_id):
        self._session = PluginStorage.open(plugin_id)
        self._host = None
        self._generation = None
        self._closed = False
        self._session_closed = False

    def bind_runtime(self, host, generation):
        if self._closed:
            raise StorageInactiveError("Plugin storage is inactive")

        self._host = host
        self._generation = generation

    def close(self):
        if self._session_closed:
            return

        self._closed = True
        self._host = None
        self._generation = None
        self._session.close()
        self._session_closed = True

    def global_scope(self):
        self._require_operation()
        return self._call(self._session.globalScope)

    def account_scope(self, account):
        self._require_operation()
        return self._call(lambda: self._session.accountScope(account))

    def read(self, scope, key):
        self._require_operation()
        payload = self._call(lambda: self._session.read(scope, key))

        if payload is None:
            return None

        value = json.loads(payload)

        if value["kind"] == "bytes":
            value["payload"] = base64.b64decode(value["payload"], validate=True)

        return _StoredValue(
            value["kind"],
            value["payload"],
            value["byte_size"],
        )

    def apply(self, operations, schema_version=None):
        self._require_operation()
        payload = {
            "operations": [self._operation(operation) for operation in operations],
            "schema_version": schema_version,
        }
        self._call(lambda: self._session.apply(json.dumps(
            payload,
            ensure_ascii=False,
            allow_nan=False,
            separators=(",", ":"),
        )))

    def statistics(self):
        self._require_operation()
        value = json.loads(self._call(self._session.statistics))
        limits = value["limits"]

        return StorageStatistics(
            value_count=value["value_count"],
            value_bytes=value["value_bytes"],
            schema_version=value["schema_version"],
            limits=StorageLimits(
                max_entries=limits["max_entries"],
                max_value_bytes=limits["max_value_bytes"],
                max_total_value_bytes=limits["max_total_value_bytes"],
                max_files=limits["max_files"],
                max_file_bytes=limits["max_file_bytes"],
                max_total_file_bytes=limits["max_total_file_bytes"],
            ),
            file_count=value["file_count"],
            file_bytes=value["file_bytes"],
        )

    def file_contains(self, scope, path):
        self._require_operation()
        return bool(self._call(lambda: self._session.fileContains(scope, path)))

    def file_read(self, scope, path):
        self._require_operation()
        value = self._call(lambda: self._session.fileRead(scope, path))
        return None if value is None else bytes(value)

    def file_write(self, scope, path, data):
        self._require_operation()
        self._call(lambda: self._session.fileWrite(scope, path, data))

    def file_delete(self, scope, path):
        self._require_operation()
        return bool(self._call(lambda: self._session.fileDelete(scope, path)))

    def file_clear(self, scope):
        self._require_operation()
        self._call(lambda: self._session.fileClear(scope))

    def submit(self, callback):
        if not self._is_active():
            return None

        status = int(self._host.submitStorage(self._generation, callback))

        if status == 2:
            return None

        return status == 0

    def complete(self, callback):
        def run():
            if self._is_active():
                callback()

        post_ui(run)

    def _operation(self, operation):
        result = {
            "action": operation.action,
            "scope": operation.scope,
            "key": operation.key,
        }

        if operation.value is not None:
            payload = operation.value.payload

            if operation.value.kind == "bytes":
                payload = base64.b64encode(payload).decode("ascii")

            result["value"] = {
                "kind": operation.value.kind,
                "payload": payload,
                "byte_size": operation.value.byte_size,
            }

        return result

    def _require_operation(self):
        self._require_active()

        if Looper.myLooper() == Looper.getMainLooper():
            raise StorageThreadError(
                "Plugin storage cannot perform direct I/O on the UI thread"
            )

    def _require_active(self):
        if not self._is_active():
            raise StorageInactiveError("Plugin storage is inactive")

    def _is_active(self):
        return (
            not self._closed
            and self._host is not None
            and self._host.isGenerationActive(self._generation)
        )

    def _call(self, callback):
        try:
            return callback()
        except Exception as error:
            self._raise_mapped(error)

    @staticmethod
    def _raise_mapped(error):
        message = str(error)
        mappings = (
            ("ACCOUNT_UNAVAILABLE:", StorageAccountUnavailableError),
            ("OWNER_CHANGED:", StorageOwnerChangedError),
            ("QUOTA_EXCEEDED:", StorageQuotaExceededError),
            ("INACTIVE:", StorageInactiveError),
            ("INVALID_REQUEST:", StorageTransactionError),
        )

        for marker, error_type in mappings:
            if marker in message:
                raise error_type(message.split(marker, 1)[1]) from error

        raise StorageError(message) from error
