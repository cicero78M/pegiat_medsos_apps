# Automator

This screen exposes a minimal shell view and a button to start automation.
Follow these steps for the recommended way to run the TikTok helper using
[UIAutomator2](https://github.com/openatx/uiautomator2):

1. Install Python 3 and run `pip install -r requirements.txt` to get the
   required packages, including `uiautomator2`.
2. Connect your Android device via USB or Wi‑Fi and enable USB debugging.
3. Launch the script from the project root:

   ```bash
   python scripts/tiktok_post.py <device-serial> /sdcard/video.mp4 --caption "Halo"
   ```
4. The script will open TikTok, select the video and publish it with the
   optional caption.

You can also execute the same command inside Termux if Python is installed on
the device.
