# Privacy

Connections sends nothing anywhere, unless you sign in to your own contacts server: then it
syncs the address books you chose with that server, and with nothing else.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## What it asks for, and why

`app/src/main/AndroidManifest.xml` declares these permissions:

- **Contacts** (`READ_CONTACTS`, `WRITE_CONTACTS`) — to show and change the phone's own address
  book, which is the whole of what the app does. Android asks you for this; you can take it back
  in Android's settings at any time.
- **Accounts** (`GET_ACCOUNTS`) — to know which accounts on the phone can keep contacts, so a new
  person can be saved to one. The only account it signs in to is the contacts server you choose.
- **Phone calls** (`CALL_PHONE`) — so a press on a number calls it. Android asks the first time
  a number is pressed. Refuse, and the number goes to the dialer instead, for you to call there.
- **Call log** (`READ_CALL_LOG`) — only to list the calls with one person on their page. Android asks
  the first time that list is opened. The log is read on the phone and never copied anywhere.
- **Calendar** (`READ_CALENDAR`, `WRITE_CALENDAR`) — only for the birthday switch in the editor. It
  puts a person's birthday in a calendar of its own, called Birthdays, and reads your calendars to
  see whether a synced one already holds birthdays. Android asks the first time the switch is used.
- **Internet** (`INTERNET`) — only to sync with your own contacts server, once you sign in to it
  (Settings → Sync with a server). That server, the one whose address you typed, is the only one
  contacted (`davsync/`, see below). Without a sign-in, nothing uses it.
- **Sync settings** (`READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS`, `READ_SYNC_STATS`) — to turn on
  and read the sync of Connections' own address-book accounts. They give no access to anything else.
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

## Your contacts server

Signing in asks for the server's address, your user name and a password; an app password made
for this phone is best, where the server has them. Nothing is kept until the server has
answered. The password is then sealed with a key in the phone's keystore, which cannot be
exported, and kept only in the phone's own account store, which no other app can read; it is
sent only to that server's domain, and over `https://` unless the address is on your home
network or a VPN (a private or Tailscale address). Each address book you sync becomes an account
of its own on the phone, as with DAVx5, so its people sit in Android's contacts store like any
others. Remove account takes the account and its people off the phone; the server keeps them.

The sync code is DAVx5's (bitfire, GPL-3.0), copied into this app in `dav4jvm/`, `synctools/`
and `davsync/`, not a separate app or service. If a person was changed on the phone and on the
server at once, the server's version is kept and the phone's is saved beside it as "(phone's
copy)", so nothing is lost.

## Checking this

Every line of the app is in this repository. Search it for `INTERNET`: the manifest says what it
is for, and the only network code is in `davsync/` and the two modules it uses.

<!-- shared-about privacy section -->
## Who we are

Connections is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Connections keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Connections is sold or shared.

Since Connections keeps what it stores only on your phone, you can exercise most of these rights yourself: see, change or delete anyone in the app (your contacts live in Android's own address book, which uninstalling the app does not touch), save them to a .vcf file, remove a server account in Settings, which takes its people off the phone, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.

If you point Connections at a server of your own, what that server keeps is yours to manage.
<!-- /shared-about privacy section -->
