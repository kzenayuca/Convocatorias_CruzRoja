#!/usr/bin/env python3
"""Build an editable StarUML model from the schema and a curated domain view.

No Java is generated or overwritten. The JSON structure follows StarUML's v7 UMLExample
(github.com/staruml/staruml-samples). GUI opening/layout still needs verification in StarUML.
"""

import argparse
import json
import re
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def identity(name):
    return "cr-" + uuid.uuid5(uuid.NAMESPACE_URL, "convocatorias/" + name).hex


def reference(name):
    return {"$ref": identity(name)}


def node(kind, key, parent=None, **values):
    result = {"_type": kind, "_id": identity(key)}
    if parent:
        result["_parent"] = reference(parent)
    result.update(values)
    return result


def schema_tables():
    sql = (ROOT / "backend/src/main/resources/db/migration/V1__legacy_schema.sql").read_text()
    sql = re.sub(r"--[^\n]*", "", sql)
    tables = {}
    for name, body in re.findall(r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\);", sql, re.S):
        tables[name] = []
        depth, start = 0, 0
        for index, char in enumerate(body + ","):
            depth += (char == "(") - (char == ")")
            if char == "," and depth == 0:
                definition = body[start:index].strip()
                start = index + 1
                if definition and not re.match(r"(?:PRIMARY|UNIQUE|CHECK|CONSTRAINT|FOREIGN)\b", definition):
                    tables[name].append(definition)
    for table, definition in re.findall(
        r"ALTER TABLE (\w+) ADD COLUMN IF NOT EXISTS ([^;]+);", sql
    ):
        tables[table].append(definition.strip())
    for table, column in re.findall(
        r"ALTER TABLE (\w+) ALTER COLUMN (\w+) DROP NOT NULL", sql
    ):
        tables[table] = [re.sub(r"\s+NOT NULL\b", "", definition)
                         if definition.split()[0] == column else definition
                         for definition in tables[table]]
    return tables


def model_class(parent, key, name, attributes, operations=(), stereotype="", documentation=""):
    result = node("UMLClass", key, parent, name=name, stereotype=stereotype,
                  documentation=documentation, ownedElements=[], attributes=[], operations=[])
    for index, (attribute, typ) in enumerate(attributes):
        result["attributes"].append(node("UMLAttribute", f"{key}/a/{index}", key,
                                         name=attribute, type=typ, visibility="private"))
    for index, operation in enumerate(operations):
        result["operations"].append(node("UMLOperation", f"{key}/o/{index}", key,
                                         name=operation, visibility="public"))
    return result


def class_view(diagram, model, index, row_top):
    key = f"{diagram}/view/{model['_id']}"
    # IDs for model objects are already generated; references to them are literal.
    model_ref = {"$ref": model["_id"]}
    left, top = 40 + (index % 3) * 460, row_top
    width = 350
    attribute_count = len(model["attributes"])
    operation_count = len(model["operations"])
    height = 55 + 17 * (attribute_count + operation_count)
    subviews = []
    name_key = key + "/name"
    labels = []
    for field, text, y, bold in (
        ("stereotypeLabel", "«" + model["stereotype"] + "»", top + 5, False),
        ("nameLabel", model["name"], top + 23, True),
        ("namespaceLabel", "", top + 23, False),
        ("propertyLabel", "", top + 23, False),
    ):
        label_key = name_key + "/" + field
        labels.append(node("LabelView", label_key, name_key, text=text, visible=bool(text),
                           font="Arial;12;" + str(int(bold)), left=left + 5, top=y,
                           width=width - 10, height=16))
    name_view = node("UMLNameCompartmentView", name_key, key, model=model_ref, subViews=labels,
                     left=left, top=top, width=width, height=43, font="Arial;12;0")
    for field, *_ in (("stereotypeLabel",), ("nameLabel",), ("namespaceLabel",), ("propertyLabel",)):
        name_view[field] = reference(name_key + "/" + field)
    subviews.append(name_view)
    fields = {"nameCompartment": reference(name_key)}
    for kind, field, members, item_type, y in (
        ("UMLAttributeCompartmentView", "attributeCompartment", model["attributes"],
         "UMLAttributeView", top + 43),
        ("UMLOperationCompartmentView", "operationCompartment", model["operations"],
         "UMLOperationView", top + 48 + 17 * attribute_count),
        ("UMLReceptionCompartmentView", "receptionCompartment", [], "", top),
        ("UMLTemplateParameterCompartmentView", "templateParameterCompartment", [], "", top),
    ):
        compartment = key + "/" + field
        items = []
        for ordinal, member in enumerate(members):
            text = member["name"] + (": " + member["type"] if "type" in member else "()")
            items.append(node(item_type, compartment + f"/{ordinal}", compartment,
                              model={"$ref": member["_id"]}, text=text, font="Arial;12;0",
                              left=left + 5, top=y + ordinal * 17, width=width - 10,
                              height=16, horizontalAlignment=0))
        subviews.append(node(kind, compartment, key, model=model_ref, subViews=items,
                             visible=bool(members), font="Arial;12;0", left=left, top=y,
                             width=width, height=max(5, 17 * len(members))))
        fields[field] = reference(compartment)
    return node("UMLClassView", key, diagram, model=model_ref, subViews=subviews,
                font="Arial;12;0", left=left, top=top, width=width, height=height,
                suppressAttributes=False, suppressOperations=False, containerChangeable=True, **fields)


def association(parent, key, source, target, name, logical=False,
                source_count="0..*", target_count="1", stereotype=None):
    return node("UMLAssociation", key, parent, name=name,
                stereotype=stereotype or ("asociación lógica sin FK" if logical else "FK declarada"),
                end1=node("UMLAssociationEnd", key + "/1", key,
                          reference={"$ref": source["_id"]}, multiplicity=source_count),
                end2=node("UMLAssociationEnd", key + "/2", key,
                          reference={"$ref": target["_id"]}, multiplicity=target_count))


def edge_view(diagram, key, relation, tail, head):
    x1, y1 = tail["left"] + tail["width"], tail["top"] + 25
    x2, y2 = head["left"], head["top"] + 25
    model = {"$ref": relation["_id"]}
    edge = node("UMLAssociationView", key, diagram, model=model, font="Arial;11;0",
                tail={"$ref": tail["_id"]}, head={"$ref": head["_id"]},
                points=f"{x1}:{y1};{x2}:{y2}", subViews=[])
    for field in ("nameLabel", "stereotypeLabel", "propertyLabel", "tailRoleNameLabel",
                  "tailPropertyLabel", "tailMultiplicityLabel", "headRoleNameLabel",
                  "headPropertyLabel", "headMultiplicityLabel"):
        label = key + "/" + field
        text = {"nameLabel": relation["name"],
                "tailMultiplicityLabel": relation["end1"]["multiplicity"],
                "headMultiplicityLabel": relation["end2"]["multiplicity"]}.get(field, "")
        edge["subViews"].append(node("EdgeLabelView", label, key, model=model,
                                     hostEdge=reference(key), text=text,
                                     visible=bool(text), font="Arial;11;0", left=(x1+x2)//2,
                                     top=(y1+y2)//2, width=240, height=16,
                                     edgePosition=2 if field.startswith("tail") else 0 if field.startswith("head") else 1,
                                     alpha=1.5707963267948966, distance=15))
        edge[field] = reference(label)
    for field in ("tailQualifiersCompartment", "headQualifiersCompartment"):
        child = key + "/" + field
        edge["subViews"].append(node("UMLQualifierCompartmentView", child, key,
                                     model=model, visible=False, font="Arial;11;0"))
        edge[field] = reference(child)
    return edge


def build():
    project = node("Project", "project", name="Convocatorias Cruz Roja")
    domain = node("UMLModel", "domain", "project", name="Dominio y comportamiento", ownedElements=[])
    database = node("UMLModel", "database", "project", name="Persistencia existente", ownedElements=[])
    project["ownedElements"] = [domain, database]
    definitions = [
        ("Volunteer", "AggregateRoot", [("id", "UUID"), ("role", "String"), ("status", "String")], ["protectOwnAdministration"]),
        ("Callout", "AggregateRoot", [("id", "UUID"), ("window", "ServiceWindow"), ("capacity", "TeamCapacity")], ["canAdmit", "complete", "requireEnrollment", "requireOperation"]),
        ("Enrollment", "Entity", [("id", "UUID"), ("volunteerId", "UUID"), ("calloutId", "UUID"), ("status", "EnrollmentStatus")], ["requireNewRequest", "cancelForReplacement", "assignResponsible"]),
        ("TeamCapacity", "ValueObject", [("total", "int"), ("graduates", "int"), ("radio", "int"), ("pilots", "int")], ["fits", "complete"]),
        ("ServiceWindow", "ValueObject", [("start", "LocalDateTime"), ("end", "LocalDateTime"), ("deadline", "LocalDateTime")], ["validatePlanning", "overlaps"]),
        ("Attendance", "AggregateRoot", [("id", "UUID"), ("status", "String")], ["confirm", "deduction"]),
        ("Justification", "AggregateRoot", [("id", "UUID"), ("status", "String")], ["resolve"]),
        ("MaterialRequest", "AggregateRoot", [("id", "UUID"), ("requested", "int"), ("received", "int")], ["requireHandover"]),
        ("Announcement", "ValueObject", [("title", "String"), ("body", "String"), ("audience", "String")], []),
        ("Notification", "Entity", [("id", "UUID"), ("recipientId", "UUID"), ("readAt", "LocalDateTime")], ["readBy"]),
        ("AnnualEnrollment", "ValueObject", [("year", "int")], ["startsOn", "endsOn"]),
        ("HourTargets", "ValueObject", [("trainingHours", "BigDecimal"), ("graduateHours", "BigDecimal")], []),
    ]
    concepts = {name: model_class("domain", "domain/" + name, name, attrs, methods, kind)
                for name, kind, attrs, methods in definitions}
    domain["ownedElements"].extend(concepts.values())
    domain_relations = []
    for source, target, name, source_count, target_count in [
        ("Callout", "TeamCapacity", "capacidad", "1", "1"),
        ("Callout", "ServiceWindow", "horario", "1", "1"),
        ("Enrollment", "Callout", "participación", "0..*", "1"),
        ("Enrollment", "Volunteer", "participante", "0..*", "1"),
        ("Attendance", "Enrollment", "resultado", "0..1", "1"),
        ("Justification", "Enrollment", "incidencia", "0..*", "1"),
        ("MaterialRequest", "Callout", "solicitud", "0..*", "1"),
        ("Notification", "Volunteer", "destinatario", "0..*", "1"),
        ("AnnualEnrollment", "Volunteer", "ciclo anual", "0..*", "1"),
    ]:
        relation = association("domain", f"domain/relation/{source}/{target}",
                               concepts[source], concepts[target], name,
                               source_count=source_count, target_count=target_count,
                               stereotype="relación de negocio")
        domain["ownedElements"].append(relation)
        domain_relations.append((source, target, relation))
    tables = {}
    foreign_keys = []
    columns = schema_tables()
    for table, definitions in columns.items():
        attrs = []
        for definition in definitions:
            column, typ = definition.split(None, 1)
            typ = re.split(r"\s+(?:NOT|DEFAULT|CHECK|REFERENCES|GENERATED|PRIMARY|UNIQUE)\b", typ)[0]
            attrs.append((column, typ))
            target = re.search(r"REFERENCES\s+(\w+)", definition)
            if target:
                foreign_keys.append((table, target[1], column, False))
        tables[table] = model_class("database", "table/" + table, table, attrs,
                                    stereotype="tabla existente")
    database["ownedElements"].extend(tables.values())
    foreign_keys += [("enrollments", "users", "volunteer_id", True),
                     ("enrollments", "callouts", "callout_id", True),
                     ("volunteer_profiles", "users", "user_id", True),
                     ("attendance_records", "enrollments", "enrollment_id", True),
                     ("justifications", "attendance_records", "absence_id", True),
                     ("material_assignments", "materials", "material_id", True),
                     ("material_assignments", "callouts", "callout_id", True),
                     ("loans", "materials", "material_id", True),
                     ("loans", "users", "volunteer_id", True),
                     ("notifications", "users", "recipient_id", True),
                     ("announcements", "users", "published_by", True)]
    relations = []
    for source, target, column, logical in foreign_keys:
        key = f"relation/{source}/{column}/{target}"
        definition = next(d for d in columns[source] if d.split()[0] == column)
        required = "NOT NULL" in definition or "PRIMARY KEY" in definition
        unique = "PRIMARY KEY" in definition or "UNIQUE" in definition
        unique |= (source, column) == ("attendance_records", "enrollment_id")
        item = association("database", key, tables[source], tables[target], column, logical,
                           source_count="0..1" if unique else "0..*",
                           target_count="1" if required else "0..1")
        database["ownedElements"].append(item)
        relations.append((source, target, item))
    groups = [
        (domain, "01 Dominio: reglas y responsabilidades", list(concepts), concepts, domain_relations),
        (database, "02 Núcleo: cuentas, convocatoria e inscripción", ["users", "volunteer_profiles", "callouts", "enrollments", "annual_enrollments", "service_code_counters", "service_code_history", "responsible_history"], tables, relations),
        (database, "03 Ejecución, incidencias y cierres", ["enrollments", "attendance_records", "attendance_review_requests", "justifications", "justification_resolutions", "monthly_reports", "monthly_report_rows", "monthly_report_adjustments"], tables, relations),
        (database, "04 Materiales: solicitud, no inventario", ["users", "callouts", "materials", "material_requests", "material_assignments", "loans"], tables, relations),
        (database, "05 Comunicaciones: comunicado y aviso personal", ["users", "announcements", "notifications"], tables, relations),
    ]
    for parent, name, names, objects, links in groups:
        key = "diagram/" + name
        diagram = node("UMLClassDiagram", key, "domain" if parent is domain else "database",
                       name=name, ownedViews=[])
        views = {}
        row_top = 40
        for start in range(0, len(names), 3):
            row_names = names[start:start + 3]
            for index, name in enumerate(row_names):
                views[name] = class_view(key, objects[name], index, row_top)
            row_top += max(views[name]["height"] for name in row_names) + 140
        diagram["ownedViews"].extend(views.values())
        for source, target, relation in links:
            if source in views and target in views:
                diagram["ownedViews"].append(edge_view(key, key + "/" + relation["_id"], relation,
                                                      views[source], views[target]))
        parent["ownedElements"].append(diagram)
    return project


def validate(project):
    ids, refs = set(), []

    def visit(value):
        if isinstance(value, dict):
            if "_id" in value:
                if value["_id"] in ids:
                    raise ValueError("Identificador duplicado: " + value["_id"])
                ids.add(value["_id"])
            if "$ref" in value:
                refs.append(value["$ref"])
            for nested in value.values():
                visit(nested)
        elif isinstance(value, list):
            for nested in value:
                visit(nested)

    visit(project)
    missing = set(refs) - ids
    if missing:
        raise ValueError("Referencias sin destino: " + str(missing))
    return len(ids)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "backend/target/model/Convocatorias.mdj")
    parser.add_argument("--check", type=Path, help="Validate references without changing the model")
    args = parser.parse_args()
    if args.check:
        project = json.loads(args.check.read_text())
        print(f"{validate(project)} elementos válidos; apertura visual en StarUML pendiente.")
    else:
        if args.output.exists():
            parser.error("El destino ya existe. Usa otro destino para no sobrescribir cambios del editor.")
        project = build()
        validate(project)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n")
        print(f"Modelo creado: {args.output}")
