# Medical-record research workspace (v1.3)

## Implemented

- Native Android multi-document picker; original documents are not uploaded to a server
- Local image and PDF OCR via ML Kit (60-page limit per PDF, 20 MB limit per file, explicit partial-import warnings)
- Word DOCX, TXT, CSV, JSON, XML and Markdown text extraction
- Source-attributed, rule-based indexing for common cardiac, autonomic, respiratory and noncardiac findings plus several measurements
- Explicit NEGATED / POSSIBLE / FAMILY_HISTORY / MENTIONED labels, with unrecognized clinical impressions queued for manual review
- User-entered missing clinical research topics
- Once the user opts into external topic searches, research refreshes on every opening and when finding topics change
- Europe PMC REST API with PubMed and PMC records, publication date ordering and abstract/identifier metadata
- Cached articles, last successful research timestamps, visible HTTP errors, links to sources
- Single on-device case file in Android non-backup private storage; ability to delete workspace
- Nothing remotely executes code or silently rewrites diagnostic rules

## Known limits and safety

This is NOT a diagnostic medical device. Keyword matches are not a validated clinical NLP pipeline. Negation is local-line context only, dates are not inferred reliably, and multi-page OCR/complex Word tables can misread clinical data. Unknown terms require manual mapping. Research retrieves metadata and available abstracts, not guaranteed full text, and does not verify all claims in a cited study. A publication match is not proof of a diagnosis and cannot estimate individual risk. Clinician review remains necessary.

A person must explicitly opt in before their topic names are searched externally. While original documents and excerpts stay on the phone, medical research terms sent to Europe PMC can still reveal sensitive information. No support for encrypted cloud sync, FHIR server connection, multiple isolated patient profiles, or clinical certification is claimed.

Maximum 60 pages per PDF, 20 MB source file, and 1.5 million text characters per import. The user-visible note flags truncation. Search failures preserve the prior cached results along with the failure message.

## Developer checks

Run Android unit tests via `./gradlew testDebugUnitTest`, which includes `MedicalFindingExtractorTest`.
The existing Python research-engine unit tests also run in GitHub Actions.

## Literature API

Europe PMC REST documentation: https://dev.europepmc.org/RestfulWebService

## Security

Do not commit real patient records or application API keys to the GitHub repository. The research workspace stores a local case in `noBackupFilesDir`; the app disables Android Auto Backup. No patient-specific names, record text or excerpts are intentionally included in literature queries, aside from explicitly typed user research topics.
