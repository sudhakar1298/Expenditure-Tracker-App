from databricks.configure_job_email import merge_email_notifications, validate_email


def test_adds_selected_events_and_preserves_existing_recipients():
    original = {
        "on_failure": ["existing@example.com"],
        "on_start": ["owner@example.com"],
    }

    result = merge_email_notifications(
        original, "new-owner@example.com", ["success", "failure"]
    )

    assert result["on_success"] == ["new-owner@example.com"]
    assert result["on_failure"] == [
        "existing@example.com",
        "new-owner@example.com",
    ]
    assert result["on_start"] == ["owner@example.com"]
    assert original == {
        "on_failure": ["existing@example.com"],
        "on_start": ["owner@example.com"],
    }


def test_does_not_duplicate_email_case_insensitively():
    result = merge_email_notifications(
        {"on_success": ["Owner@Example.com"]},
        "owner@example.com",
        ["success"],
    )

    assert result["on_success"] == ["Owner@Example.com"]


def test_validates_email():
    assert validate_email(" owner@example.com ") == (
        "owner@example.com"
    )
