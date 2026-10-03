import json


def before_send(event_json):
    event = json.loads(event_json)

    account = event["account"]
    text_length = event["text_length"]
    scheduled = event["schedule_date"] != 0

    return (
        f"before_send: account={account}, "
        f"length={text_length}, "
        f"scheduled={str(scheduled).lower()}"
    )