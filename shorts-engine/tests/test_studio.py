import json
import unittest
from unittest import mock

from studio import __main__ as cli
from studio.ideas import validate
from studio.render import caption_chunks, fmt_number
from studio.telegram import Telegram
from studio.tts import estimate_word_times
from studio.youtube_upload import build_metadata

BANK = json.loads((cli.BANK).read_text())


class BankTests(unittest.TestCase):
    def test_every_bank_story_passes_validation(self):
        for story in BANK["stories"]:
            self.assertEqual(validate(story), [], story["id"])

    def test_ids_are_unique(self):
        ids = [s["id"] for s in BANK["stories"]]
        self.assertEqual(len(ids), len(set(ids)))


class RenderHelperTests(unittest.TestCase):
    def test_numbers_keep_the_story_precision(self):
        show = {"value": 1.75, "prefix": "$", "suffix": "B"}
        self.assertEqual(fmt_number(1.75, show), "$1.75B")
        self.assertEqual(fmt_number(0.5, show), "$0.50B")
        self.assertEqual(fmt_number(10000, {"value": 10000, "suffix": " BTC"}), "10,000 BTC")

    def test_captions_break_at_punctuation_and_three_words(self):
        words = [(w, i, i + 1) for i, w in enumerate("He sold it, for eight hundred dollars.".split())]
        self.assertEqual([[w[0] for w in c] for c in caption_chunks(words)],
                         [["He", "sold", "it,"], ["for", "eight", "hundred"], ["dollars."]])

    def test_estimated_word_times_fill_the_clip_in_order(self):
        words = estimate_word_times("One two, three.", 3.0)
        self.assertEqual([w[0] for w in words], ["One", "two,", "three."])
        self.assertTrue(all(a[2] <= b[1] + 1e-9 for a, b in zip(words, words[1:])))
        self.assertLessEqual(words[-1][2], 3.0)


class UploadMetadataTests(unittest.TestCase):
    def test_metadata_is_not_for_kids_and_cites_sources(self):
        meta = build_metadata(BANK["stories"][0], privacy="private")
        self.assertFalse(meta["status"]["selfDeclaredMadeForKids"])
        self.assertEqual(meta["status"]["privacyStatus"], "private")
        self.assertIn("wikipedia.org", meta["snippet"]["description"])
        self.assertLessEqual(len(meta["snippet"]["title"]), 100)


class TelegramTests(unittest.TestCase):
    def test_only_button_taps_from_our_chat_count(self):
        tg = Telegram("t", 42)
        updates = [
            {"update_id": 5, "callback_query": {"id": "a", "data": "ok:kodak", "message": {"chat": {"id": 42}}}},
            {"update_id": 6, "callback_query": {"id": "b", "data": "ok:quibi", "message": {"chat": {"id": 99}}}},
            {"update_id": 7, "callback_query": {"id": "c", "data": "no:nokia", "message": {"chat": {"id": 42}}}},
            {"update_id": 8, "message": {"text": "hi", "chat": {"id": 42}}},
        ]
        with mock.patch.object(tg, "updates", return_value=updates), mock.patch.object(tg, "_call"):
            found, offset = tg.decisions(3)
        self.assertEqual(found, {"kodak": "approved", "nokia": "rejected"})
        self.assertEqual(offset, 9)


class PollTests(unittest.TestCase):
    def test_approved_video_is_uploaded_and_state_updated(self):
        state = {"items": {"kodak-digital-camera": {"episode": 3, "status": "pending", "file_id": "F"}},
                 "telegram_offset": None}
        tg = mock.Mock()
        tg.decisions.return_value = ({"kodak-digital-camera": "approved"}, 11)
        env = {"YT_CLIENT_ID": "i", "YT_CLIENT_SECRET": "s", "YT_REFRESH_TOKEN": "r"}
        saved = {}
        with mock.patch.object(cli, "telegram", return_value=tg), \
                mock.patch.object(cli, "load_state", return_value=state), \
                mock.patch.object(cli, "save_state", side_effect=lambda s: saved.update(json.loads(json.dumps(s)))), \
                mock.patch.dict("os.environ", env), \
                mock.patch("studio.youtube_upload.access_token", return_value="tok"), \
                mock.patch("studio.youtube_upload.upload", return_value="VID123") as up:
            cli.main(["poll"])
        item = saved["items"]["kodak-digital-camera"]
        self.assertEqual(item["status"], "uploaded")
        self.assertEqual(item["youtube_id"], "VID123")
        self.assertEqual(saved["telegram_offset"], 11)
        self.assertEqual(up.call_args[0][1]["status"]["privacyStatus"], "private")


if __name__ == "__main__":
    unittest.main()
