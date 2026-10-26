"""Exploratory four-axis low-flow phenotyping.

No combination of numeric thresholds automatically establishes Stage C
heart failure or a confirmed HFpEF diagnosis.
"""
from __future__ import annotations
from typing import Any, Mapping
import math

METRICS = ("svi", "hr", "lvedvi", "lvmi", "lvef", "gls", "lasr", "rvfwls", "ntprobnp", "ee")
LEGACY = {
    "new_svi": "svi", "new_lvedvi": "lvedvi", "new_lvmi": "lvmi",
    "new_lvef": "lvef", "new_gls": "gls", "new_lasr": "lasr",
    "new_rvfwls": "rvfwls", "new_bnp": "ntprobnp", "new_ee": "ee",
}

def _num(data: Mapping[str, Any], key: str) -> float | None:
    raw = data.get(key)
    if raw is None or raw == "":
        return None
    value = float(raw)
    if not math.isfinite(value) or value < 0:
        raise ValueError(f"{key} must be a finite nonnegative number")
    if key == "lvef" and value > 100:
        raise ValueError("LVEF cannot exceed 100%")
    return value

def assess_heart_failure_phenotype(patient: Mapping[str, Any]) -> dict[str, Any]:
    values = {key: _num(patient, key) for key in METRICS}
    svi, hr, edvi, mi, ef = (values[k] for k in ("svi", "hr", "lvedvi", "lvmi", "lvef"))
    expected_svi = edvi * ef / 100 if edvi is not None and ef is not None else None
    discordance = (
        abs(expected_svi - svi) / max(expected_svi, svi, 1e-9) > 0.25
        if expected_svi is not None and svi is not None else None
    )
    return {
        "tool_type": "exploratory rules-based phenotype research",
        "clinical_diagnosis": None,
        "stage_c_confirmed": False,
        "measurements": values,
        "four_axis_findings": {
            "flow": {
                "low_svi_research_flag": (svi < 35) if svi is not None else None,
                "derived_cardiac_index_l_min_m2": round(svi * hr / 1000, 3)
                if svi is not None and hr is not None else None,
            },
            "geometry": {
                "estimated_mcf_percent": round(svi / (mi / 1.05) * 100, 2)
                if svi is not None and mi is not None and mi > 0 else None,
                "volume_derived_svi_ml_m2": round(expected_svi, 2)
                if expected_svi is not None else None,
                "volume_svi_discordance_gt_25pct": discordance,
            },
            "strain": {
                "illustrative_low_lv_gls_magnitude": values["gls"] < 16
                if values["gls"] is not None else None,
                "illustrative_low_la_reservoir": values["lasr"] < 18
                if values["lasr"] is not None else None,
            },
            "clinical_context": {
                "symptoms_documented": bool(patient.get("symptoms_documented", False)),
                "cardiac_abnormality_documented": bool(patient.get("cardiac_abnormality_documented", False)),
                "objective_congestion_documented": bool(patient.get("objective_congestion_documented", False)),
                "ntprobnp_pg_ml": values["ntprobnp"],
                "resting_e_over_eprime": values["ee"],
            },
        },
        "limitations": [
            "Low SVI alone does not establish heart failure or its stage.",
            "EF, BNP and resting E/e' must be interpreted with all clinical evidence.",
            "Cardiac index and myocardial contraction fraction are derived approximations.",
            "Flags are not model-calibrated probabilities or SHAP attributions.",
        ],
    }

def classify_heart_failure(patient: Mapping[str, Any]) -> dict[str, Any]:
    """Compatible entry point for prior new_* parameter names, without default values."""
    normalized = dict(patient)
    for old, new in LEGACY.items():
        if new not in normalized and old in normalized:
            normalized[new] = normalized[old]
    return assess_heart_failure_phenotype(normalized)

if __name__ == "__main__":
    import json
    print(json.dumps(assess_heart_failure_phenotype({}), indent=2))
