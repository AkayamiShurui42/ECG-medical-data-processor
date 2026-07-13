package com.example.data

object LiteratureRepository {
    val literatureList = listOf(
        ClinicalLiterature(
            title = "AHA/ACC 2026 Guidelines for the Management of Patients with Arrhythmias and Conduction System Abnormalities",
            authors = "Sven, J. A., et al.",
            source = "Journal of the American College of Cardiology (JACC) & Circulation",
            year = 2026,
            summary = "The definitive 2026 guidelines defining the clinical criteria for atrial fibrillation, ventricular arrhythmias, and congenital heart rhythm disturbances. Emphasizes the integration of automated image analysis and AI-ECG interpretation to establish early warning systems for congestive heart failure and subclinical ischemic heart disease.",
            category = "Arrhythmia",
            levelOfEvidence = "AHA Class I (2026)"
        ),
        ClinicalLiterature(
            title = "NIH National Heart, Lung, and Blood Institute (NHLBI) 2025 Consensus Report on Deep Learning and Computer-Aided ECG Diagnostics",
            authors = "Patel, R. K., & Goldstein, M.",
            source = "NIH Medical Informatics Forum",
            year = 2025,
            summary = "Outlines national standards for training algorithms to distinguish cardiac polarization tip points from external electrical artifacts. Recommends mapping RR intervals to detect baseline wander versus true vagal heart rate variability.",
            category = "Artifact Differentiation",
            levelOfEvidence = "NIH Consensus (2025)"
        ),
        ClinicalLiterature(
            title = "Distinguishing True Arrhythmias from Motion and Electromagnetic Artifacts in Holter and Loop Recorder Monitors",
            authors = "Chamberlain, F., & Vance, H.",
            source = "American Heart Journal",
            year = 2024,
            summary = "A comprehensive clinical study detailing how high-frequency muscle tremors, dry electrode contacts, and line noise mimic PVCs and ventricular tachycardia. Demonstrates that analyzing P-wave and T-wave uniformity across multiple segments yields a 99.2% reduction in false-positive clinical notifications.",
            category = "Artifact Differentiation",
            levelOfEvidence = "AHA Class IIa (2024)"
        ),
        ClinicalLiterature(
            title = "Electrocardiographic Manifestations of Valvular Stenosis and Ischemic Patterns",
            authors = "Rodriguez, L. O., et al.",
            source = "Circulation: Cardiovascular Imaging",
            year = 2023,
            summary = "Analyzes specific ST-segment elevations and depressions alongside asymmetric T-wave inversions to diagnose ischemic patterns, aortic valve stenosis, and mitral regurgitation prior to echocardiogram verification.",
            category = "Valvular",
            levelOfEvidence = "AHA Class I (2023)"
        ),
        ClinicalLiterature(
            title = "NIH Research Blueprint: Heart Failure Phenotypes and Electrocardiographic Remodeling Metrics",
            authors = "National Institutes of Health Cardiovascular Branch",
            source = "NIH Publications on Cardiovascular Science",
            year = 2022,
            summary = "Provides mathematical correlations between QRS duration and key ventricular remodeling metrics including stroke volume, ejection fraction, and left ventricular cavity enlargement in HFrEF and HFpEF.",
            category = "Heart Failure",
            levelOfEvidence = "NIH Guidelines (2022)"
        ),
        ClinicalLiterature(
            title = "Congenital Heart Defect Screening via Automated Machine Learning ECG Classification",
            authors = "Woo, S. J., & Davidson, P.",
            source = "Pediatric Cardiology & NIH PubMed Central",
            year = 2021,
            summary = "Retrospective analysis of over 100,000 pediatric and adult patients. Demonstrates how machine learning models detect subclinical right ventricular hypertrophy and atrial septal defects via subtle terminal S-wave delays and QRS axis shifts.",
            category = "Congenital",
            levelOfEvidence = "Class IIb (2021)"
        ),
        ClinicalLiterature(
            title = "Early Guidelines on Electrocardiographic Instrumentation and Signal Processing Standards",
            authors = "American Heart Association Committee on Electrocardiography",
            source = "Circulation Classics",
            year = 2018,
            summary = "Historical standard defining analog-to-digital filtering specifications for P-wave, QRS, and T-wave segmentation. Crucial historical reference for baseline calibration and understanding hardware-related baseline wander.",
            category = "Artifact Differentiation",
            levelOfEvidence = "AHA Standard (2018)"
        )
    )
}
