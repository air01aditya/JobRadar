package com.jobradar.app.data.discovery.sources

/**
 * Big recruiters of Indian freshers whose public job boards were checked by hand (Oct 2026) to
 * return India openings. Scanned on the fast 15-minute cycle, so their new postings show up
 * well before aggregators re-list them. Workday slugs are "tenant|wdInstance|siteName".
 *
 * Left out on purpose: Walmart, Dell, Qualcomm (their Workday refuses API requests), and
 * Amazon/Google/Microsoft/Infosys/TCS/Accenture (custom career sites, not a standard ATS).
 */
object PriorityEmployers {
    val boards: List<CompanyBoard> = listOf(
        // Indian product companies
        CompanyBoard("Swiggy", "smartrecruiters", "swiggy"),
        CompanyBoard("Paytm", "lever", "paytm"),
        CompanyBoard("Meesho", "lever", "meesho"),
        CompanyBoard("Razorpay", "greenhouse", "razorpaysoftwareprivatelimited"),
        CompanyBoard("CRED", "lever", "cred"),
        CompanyBoard("Groww", "greenhouse", "groww"),
        CompanyBoard("Zeta", "lever", "zeta"),
        CompanyBoard("HighRadius", "greenhouse", "highradius"),
        CompanyBoard("InMobi", "greenhouse", "inmobi"),
        CompanyBoard("Freshworks", "smartrecruiters", "freshworks"),
        CompanyBoard("Druva", "greenhouse", "druva"),
        CompanyBoard("Slice", "greenhouse", "slice"),
        CompanyBoard("Mindtickle", "lever", "mindtickle"),
        // Global companies hiring heavily in India
        CompanyBoard("Citi", "workday", "citi|wd5|2"),
        CompanyBoard("Kyndryl", "workday", "kyndryl|wd5|KyndrylProfessionalCareers"),
        CompanyBoard("NVIDIA", "workday", "nvidia|wd5|NVIDIAExternalCareerSite"),
        CompanyBoard("Salesforce", "workday", "salesforce|wd12|External_Career_Site"),
        CompanyBoard("Morgan Stanley", "workday", "ms|wd5|External"),
        CompanyBoard("Mastercard", "workday", "mastercard|wd1|CorporateCareers"),
        CompanyBoard("Micron", "workday", "micron|wd1|External"),
        CompanyBoard("Analog Devices", "workday", "analogdevices|wd1|External"),
        CompanyBoard("HP", "workday", "hp|wd5|ExternalCareerSite"),
        CompanyBoard("Target", "workday", "target|wd5|targetcareers"),
        CompanyBoard("Adobe", "workday", "adobe|wd5|external_experienced"),
        CompanyBoard("Intel", "workday", "intel|wd1|External"),
        CompanyBoard("PwC", "workday", "pwc|wd3|Global_Experienced_Careers"),
        CompanyBoard("Barclays", "workday", "barclays|wd3|External_Career_Site_Barclays"),
        CompanyBoard("PayPal", "workday", "paypal|wd1|jobs"),
        CompanyBoard("Fidelity", "workday", "fmr|wd1|FidelityCareers"),
        CompanyBoard("ServiceNow", "smartrecruiters", "servicenow"),
        CompanyBoard("Bosch", "smartrecruiters", "BoschGroup"),
        CompanyBoard("Thoughtworks", "greenhouse", "thoughtworks"),
        CompanyBoard("Ubisoft", "smartrecruiters", "Ubisoft2"),
    )

    private val keys = boards.map { "${it.atsType}:${it.slug.lowercase()}" }.toSet()

    /** True if [board] is already scanned here, so the hourly dataset scan can skip it. */
    fun covers(board: CompanyBoard): Boolean = "${board.atsType}:${board.slug.lowercase()}" in keys
}
