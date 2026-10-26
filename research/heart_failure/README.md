# Low-flow & heart-failure phenotype research

Adapted from the uploaded CardioSubType AI prototype ZIP and integrated with the existing ECG medical data processor rather than replacing the ECG engine.

The uploaded separate Android app and its workflow were not directly buildable as delivered: the workflow referenced an absent global-research-sync Python file. The large HTML page embedded patient-specific example details, requested GitHub personal access tokens, and made unsupported automatic Stage C and model-attribution claims.

The integrated implementation:
- runs an offline cardiac phenotype worksheet inside the existing Android app;
- shows flow, geometry, strain and clinical context separately;
- computes indexed cardiac output and approximate myocardial contraction fraction;
- checks SVI/EDV/EF consistency;
- treats missing clinical data as missing, with no silent patient defaults;
- never automatically confirms Stage C HF or HFpEF;
- includes reproducible Python tests in the regular GitHub Actions build.

The rules are exploratory and are not calibrated clinical diagnostic probabilities. Numeric boundaries depend on modality, context and evidence quality. No automatic live research harvest is claimed.

Evidence:
- https://www.ahajournals.org/doi/10.1161/CIR.0000000000001455
- https://www.jacc.org/doi/10.1016/j.jacc.2026.06.018
- https://academic.oup.com/eurheartj/article/47/38/5346/8766302

Privacy: do not commit personal health records or GitHub credentials to this public repository.
