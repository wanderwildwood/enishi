# Privacy

Contacts sends nothing anywhere. It cannot: it has no permission to use the internet.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## What it asks for, and why

`app/src/main/AndroidManifest.xml` declares five permissions, and `INTERNET` is not one of them.

- **Contacts** (`READ_CONTACTS`, `WRITE_CONTACTS`) — to show and change the phone's own address
  book, which is the whole of what the app does. Android asks you for this; you can take it back
  in Android's settings at any time.
- **Accounts** (`GET_ACCOUNTS`) — to know which accounts on the phone can keep contacts, so a new
  person can be saved to one. The app never signs in to anything.
- **Phone calls** (`CALL_PHONE`) — so a press on a number calls it. Android asks the first time
  a number is pressed. Refuse, and the number goes to the dialer instead, for you to call there.
- **Start-up** (`RECEIVE_BOOT_COMPLETED`) — only so automatic backups, if you turn them on, are
  still scheduled after the phone restarts. Nothing of the app's runs at start-up; Android's
  own job scheduler keeps the schedule.

## Where your contacts are

In Android's own contacts store, where Messaging, the dialer and every other app with contacts
access already read them. The app keeps no copy of its own and no database of its own. Its
only private storage is its settings: how names are sorted, how they are shown, where new
people go, and whether and where backups are saved. These are left out of Android's backups (`allowBackup="false"`).

## Files

A .vcf is read only when you open one, and written only where you choose to save it — by
hand, or by automatic backups into the one folder you chose for them. Sharing a
person writes their card to the app's private cache and hands it to the app you share it with,
for that one share.

## Checking this

Every line of the app is in this repository. Search it for `INTERNET` — the only hit is the
comment in the manifest saying it is not there.
