"""Checks for model consistency; these do not replace opening the file in StarUML."""

import unittest

from build_model import build, schema_tables, validate


class ModelTest(unittest.TestCase):
    def test_generated_model_has_resolvable_references_and_domain_relationships(self):
        project = build()
        self.assertGreater(validate(project), 1000)
        domain = project["ownedElements"][0]
        relationships = [item for item in domain["ownedElements"]
                         if item["_type"] == "UMLAssociation"]
        self.assertEqual(9, len(relationships))

    def test_schema_preserves_nullable_justification_attendance(self):
        absence = next(column for column in schema_tables()["justifications"]
                       if column.startswith("absence_id "))
        self.assertNotIn("NOT NULL", absence)

    def test_class_boxes_do_not_overlap(self):
        for model in build()["ownedElements"]:
            for diagram in model["ownedElements"]:
                if diagram["_type"] != "UMLClassDiagram":
                    continue
                boxes = [view for view in diagram["ownedViews"]
                         if view["_type"] == "UMLClassView"]
                for index, box in enumerate(boxes):
                    for other in boxes[index + 1:]:
                        separated = (box["left"] + box["width"] <= other["left"]
                                     or other["left"] + other["width"] <= box["left"]
                                     or box["top"] + box["height"] <= other["top"]
                                     or other["top"] + other["height"] <= box["top"])
                        self.assertTrue(separated, diagram["name"])

    def test_duplicate_ids_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "duplicado"):
            validate([{"_id": "same"}, {"_id": "same"}])

    def test_unresolved_references_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "sin destino"):
            validate({"$ref": "missing"})


if __name__ == "__main__":
    unittest.main()
