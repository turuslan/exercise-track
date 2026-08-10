# Exercise Track

You want to be strong.\
Not just your body, but your mind too.\
You want to have a discipline.\
You want to build a habit.\
You may think you have no time to spare.\
But 10 minutes a day is enough.

Saitama from "One punch man" did 100 squats and 100 pushups a day.\
Maybe you can't do that in one go.\
But what if you split it in smaller pieces.\
You can do 10 repeats, and do that 10 times a day.\
The sum is 100 repeats a day.

This app helps you see your progress,\
and decide if it's time for next exercise.

App features data synchronization with mDNS discovery.\
Add other device to pull updates from it.

## NodeJS

Start app.

```bash
npm run start
```

You can see how many times you did exercises today,
and how much time has passed since last one.

Press `Enter` to add exercise time.

Press `d` to show device list.\
Press `d` or `Esc` to hide device list.\
Use this device uid to add this device to other.\
Use up/down arrows to navigate devices.\
Press `+` when unknown device selected to add it.\
Press `-` when added device selected to remove it.

## Android
Android app shows same info as NodeJS app.

Press big button to add time.\
Same button appears in notification.\
Notification appears on lock screen too.

Add and remove devices with `+` and `-` buttons.

To save battery, there is "Network" checkbox.\
Device discovery and synchronization work when it is checked.\
Use "Exit" button to close notification and stop network.
