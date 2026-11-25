import struct
import unittest
from export_design_captures import INVENTORY, validate_png, source_version


class DesignInventoryTest(unittest.TestCase):
    def test_every_page_has_at_least_one_checkpoint_and_names_are_unique(self):
        names=[row["name"] for row in INVENTORY["captures"]]
        self.assertEqual(len(names),len(set(names)))
        self.assertEqual({name[:3] for name in names},set(INVENTORY["primaryIds"]))
        self.assertEqual(len(INVENTORY["primaryIds"]),34)
        self.assertTrue(all(row["dataType"] in ("compose-fixture","component-fixture","demo-navigation") for row in INVENTORY["captures"]))

    def test_png_dimensions_and_end_marker(self):
        sample=b"\x89PNG\r\n\x1a\n"+b"\x00\x00\x00\rIHDR"+struct.pack(">II",1080,2400)+b"\0"*12+b"\x00\x00\x00\x00IEND\xaeB`\x82"
        validate_png(sample,(1080,2400))
        with self.assertRaises(ValueError):
            validate_png(sample,(1600,2560))
        with self.assertRaises(ValueError):
            validate_png(sample[:-1],(1080,2400))

    def test_source_version(self):
        version,code=source_version()
        self.assertTrue(version)
        self.assertGreater(code,0)
