"""Persona: a user-declared demographic category set once at app install (per your instruction --
"when user installs the app they automatically set this information of region and preferred
persona in the account"). Lives on UserMap for real users; synthesized here (independent of
region/message content -- we have no real ground truth linking persona to fraud type, so
correlating them would be presenting a fabricated pattern as if it were observed data) for the
enrichment of the existing CSVs, which have no real registered users behind them.

`is_vulnerable_group` (senior_citizen, homemaker) is what the alerting step already prioritizes
for notification targeting -- persona generalizes that same flag into clustering too, per your
instruction that persona should influence which campaign a message lands in, not just who gets
alerted about it.
"""

PERSONAS: dict[str, tuple[float, bool]] = {
    # name -> (population sampling weight, is_vulnerable_group)
    "general": (0.40, False),
    "salaried_professional": (0.25, False),
    "student": (0.15, False),
    "senior_citizen": (0.10, True),
    "business_owner": (0.06, False),
    "homemaker": (0.04, True),
}

ALL_PERSONAS = list(PERSONAS.keys())


def persona_names_and_weights() -> tuple[list[str], list[float]]:
    names = list(PERSONAS.keys())
    weights = [PERSONAS[n][0] for n in names]
    return names, weights


def is_vulnerable(persona: str) -> bool:
    return PERSONAS.get(persona, (0.0, False))[1]
