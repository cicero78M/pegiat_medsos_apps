"""Post a video to TikTok using UIAutomator2."""
import argparse
import os
import time

import uiautomator2 as u2


def connect_device(addr: str):
    """Connect to the Android device at the given address."""
    return u2.connect(addr)


def upload_video(d, video_path: str, caption: str = ""):
    """Post a video to TikTok using the installed app."""
    if not os.path.exists(video_path):
        raise FileNotFoundError(video_path)

    d.app_start("com.zhiliaoapp.musically")
    time.sleep(5)

    # open upload interface (the + button)
    if d(descriptionContains="Create").exists:
        d(descriptionContains="Create").click()
    elif d(resourceId="com.zhiliaoapp.musically:id/btn_post").exists:
        d(resourceId="com.zhiliaoapp.musically:id/btn_post").click()
    else:
        raise RuntimeError("Cannot find create post button")
    time.sleep(3)

    # choose upload from gallery
    if d(textContains="Upload").exists:
        d(textContains="Upload").click()
    time.sleep(2)

    # select the first video
    d(className="android.widget.RelativeLayout", index=0).click()
    time.sleep(2)

    # confirm selection
    if d(text="Next").exists:
        d(text="Next").click()
    time.sleep(3)

    # set caption
    if caption:
        if d(resourceId="com.zhiliaoapp.musically:id/desc_edit").exists:
            d(resourceId="com.zhiliaoapp.musically:id/desc_edit").set_text(caption)
        time.sleep(1)

    # post
    if d(text="Post").exists:
        d(text="Post").click()
    else:
        raise RuntimeError("Cannot find post button")
    print("Upload initiated")


def main():
    parser = argparse.ArgumentParser(description="Upload video to TikTok via uiautomator2")
    parser.add_argument("addr", help="device serial or IP:port")
    parser.add_argument("video", help="path to video on device")
    parser.add_argument("--caption", default="", help="video caption")
    args = parser.parse_args()

    device = connect_device(args.addr)
    upload_video(device, args.video, args.caption)


if __name__ == "__main__":
    main()

