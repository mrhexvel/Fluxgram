from fluxgram.tasks import TaskInactiveError


class AndroidTasksPort:
    def __init__(self):
        self._host = None
        self._generation = None
        self._closed = False

    def bind_runtime(self, host, generation):
        if self._closed:
            raise TaskInactiveError("Managed tasks are inactive")

        self._host = host
        self._generation = generation

    def schedule(self, task_id, delay_ms, interval_ms, callback, completion):
        if not self._active():
            return None

        status = int(self._host.scheduleManagedTask(
            self._generation,
            task_id,
            delay_ms,
            interval_ms,
            callback,
            completion,
        ))

        if status == 2:
            return None

        return status == 0

    def cancel(self, task_id):
        if self._host is not None and self._generation is not None:
            self._host.cancelManagedTask(self._generation, task_id)

    def active(self, task_id):
        return bool(
            self._active()
            and self._host.isManagedTaskActive(self._generation, task_id)
        )

    def close(self):
        if self._closed:
            return

        self._closed = True

        if self._host is not None and self._generation is not None:
            self._host.cancelManagedTasks(self._generation)

        self._host = None
        self._generation = None

    def _active(self):
        return (
            not self._closed
            and self._host is not None
            and self._host.isGenerationActive(self._generation)
        )
