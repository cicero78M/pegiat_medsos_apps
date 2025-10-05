# Autopost retry skip verification

These steps confirm that a failure in one task no longer stops the rest of the autopost queue.

## Prerequisites
- A build that includes the updated retry handling (May 2024 or later).
- At least two autopost tasks available for the authenticated account.
- Ability to toggle the device's network connection.

## Steps
1. Launch the app and navigate to the Autopost screen.
2. Begin the autopost workflow.
3. As soon as the first task reaches the "Mengunduh konten…" step, disable all network connectivity (Wi‑Fi and cellular).
4. Wait for the three retry attempts to finish and observe the log entry `Berhenti pada proses download konten` followed by `Lewati tugas karena gagal mengunduh konten`.
5. Re-enable network connectivity before the next task starts.
6. Confirm that the workflow proceeds to the second task, downloads the content, and uploads to Instagram successfully.
7. Verify that the log includes the success messages for the subsequent task(s) and that the workflow completes with `Selesai`.

## Expected result
- The failed task is logged and skipped after the retry attempts.
- Later tasks in the same run continue processing normally.
