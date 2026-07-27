package com.example.api

enum class CAInterPaperSubject(val subjectCode: String, val title: String, val totalMarks: Int) {
    PAPER_1("P1", "Paper 1: Advanced Accounting", 100),
    PAPER_2A("P2A", "Paper 2A: Corporate Law & LLP", 70),
    PAPER_2B("P2B", "Paper 2B: Other Laws", 30),
    PAPER_3A("P3A", "Paper 3A: Income Tax Law", 50),
    PAPER_3B("P3B", "Paper 3B: Goods and Services Tax (GST)", 50),
    PAPER_4("P4", "Paper 4: Cost and Management Accounting", 100),
    PAPER_5("P5", "Paper 5: Auditing and Ethics", 100),
    PAPER_6A("P6A", "Paper 6A: Financial Management", 50),
    PAPER_6B("P6B", "Paper 6B: Strategic Management", 50);

    companion object {
        fun fromCode(code: String): CAInterPaperSubject {
            return entries.firstOrNull { code.startsWith(it.subjectCode) } ?: PAPER_1
        }
    }
}

data class SyllabusTopicNode(
    val topicId: String,          // Unique ID (e.g., "P3B_CH3_ITC")
    val subject: CAInterPaperSubject,
    val chapterName: String,      // e.g., "Ch 3: Basic Concepts of GST"
    val subTopicTitle: String     // e.g., "Input Tax Credit"
)

object SyllabusRegistry {
    val allTopics = listOf(
        // ================= PAPER 1: ADVANCED ACCOUNTING (100M) =================
        SyllabusTopicNode("P1_CH1_FRM", CAInterPaperSubject.PAPER_1, "Ch 1: Accounting Standards Framework", "AS Formulation, IFRS Convergence & Carve outs"),
        SyllabusTopicNode("P1_CH1_PREP", CAInterPaperSubject.PAPER_1, "Ch 1: Accounting Standards Framework", "Framework for Preparation & Presentation of FS"),
        SyllabusTopicNode("P1_AS1", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 1: Disclosure of Accounting Policies"),
        SyllabusTopicNode("P1_AS2", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 2: Valuation of Inventories"),
        SyllabusTopicNode("P1_AS3", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 3: Cash Flow Statements"),
        SyllabusTopicNode("P1_AS4", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 4: Contingencies & Events After BS Date"),
        SyllabusTopicNode("P1_AS5", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 5: Net Profit/Loss, Prior Period & Policies"),
        SyllabusTopicNode("P1_AS7", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 7: Construction Contracts"),
        SyllabusTopicNode("P1_AS9", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 9: Revenue Recognition"),
        SyllabusTopicNode("P1_AS10", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 10: Property, Plant and Equipment"),
        SyllabusTopicNode("P1_AS11", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 11: Foreign Exchange Rates"),
        SyllabusTopicNode("P1_AS12", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 12: Government Grants"),
        SyllabusTopicNode("P1_AS13", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 13: Accounting for Investments"),
        SyllabusTopicNode("P1_AS14", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 14: Amalgamations"),
        SyllabusTopicNode("P1_AS15", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 15: Employee Benefits"),
        SyllabusTopicNode("P1_AS16", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 16: Borrowing Costs"),
        SyllabusTopicNode("P1_AS17", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 17: Segment Reporting"),
        SyllabusTopicNode("P1_AS18", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 18: Related Party Disclosures"),
        SyllabusTopicNode("P1_AS19", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 19: Leases"),
        SyllabusTopicNode("P1_AS20", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 20: Earnings Per Share"),
        SyllabusTopicNode("P1_AS21", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 21: Consolidated Financial Statements"),
        SyllabusTopicNode("P1_AS22", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 22: Taxes on Income"),
        SyllabusTopicNode("P1_AS23", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 23: Investment in Associates in CFS"),
        SyllabusTopicNode("P1_AS24", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 24: Discontinuing Operations"),
        SyllabusTopicNode("P1_AS25", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 25: Interim Financial Reporting"),
        SyllabusTopicNode("P1_AS26", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 26: Intangible Assets"),
        SyllabusTopicNode("P1_AS27", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 27: Interests in Joint Ventures"),
        SyllabusTopicNode("P1_AS28", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 28: Impairment of Assets"),
        SyllabusTopicNode("P1_AS29", CAInterPaperSubject.PAPER_1, "Ch 2: Application of Accounting Standards", "AS 29: Provisions, Contingent Liabilities & Assets"),
        SyllabusTopicNode("P1_CH3_SCH3", CAInterPaperSubject.PAPER_1, "Ch 3: Company Accounts", "Schedule III (Division I) - FS Preparation"),
        SyllabusTopicNode("P1_CH3_BUY", CAInterPaperSubject.PAPER_1, "Ch 3: Company Accounts", "Buy back of securities"),
        SyllabusTopicNode("P1_CH3_REC", CAInterPaperSubject.PAPER_1, "Ch 3: Company Accounts", "Accounting for Reconstruction of Companies"),
        SyllabusTopicNode("P1_CH4_BR", CAInterPaperSubject.PAPER_1, "Ch 4: Branch Accounting", "Accounting for Branches including Foreign Branches"),

        // ================= PAPER 2A: CORPORATE LAW & LLP (70M) =================
        SyllabusTopicNode("P2A_CH1", CAInterPaperSubject.PAPER_2A, "Ch 1: Preliminary", "Important Definitions & Applicability"),
        SyllabusTopicNode("P2A_CH2", CAInterPaperSubject.PAPER_2A, "Ch 2: Incorporation of Company and Matters Incidental thereto", "Incorporation and Matters Incidental thereto"),
        SyllabusTopicNode("P2A_CH3", CAInterPaperSubject.PAPER_2A, "Ch 3: Prospectus and Allotment of Securities", "Prospectus and Allotment of Securities"),
        SyllabusTopicNode("P2A_CH4", CAInterPaperSubject.PAPER_2A, "Ch 4: Share Capital and Debentures", "Share Capital, Voting Rights and Debentures"),
        SyllabusTopicNode("P2A_CH5", CAInterPaperSubject.PAPER_2A, "Ch 5: Acceptance of Deposits by companies", "Acceptance of Deposits by Companies"),
        SyllabusTopicNode("P2A_CH6", CAInterPaperSubject.PAPER_2A, "Ch 6: Registration of Charges", "Registration & Satisfaction of Charges"),
        SyllabusTopicNode("P2A_CH7", CAInterPaperSubject.PAPER_2A, "Ch 7: Management and Administration", "Registers, Annual Returns & General Meetings"),
        SyllabusTopicNode("P2A_CH8", CAInterPaperSubject.PAPER_2A, "Ch 8: Declaration and Payment of Dividend", "Declaration and Payment of Dividend"),
        SyllabusTopicNode("P2A_CH9", CAInterPaperSubject.PAPER_2A, "Ch 9: Accounts of Companies", "Maintenance of Books, CSR & Financial Statements"),
        SyllabusTopicNode("P2A_CH10", CAInterPaperSubject.PAPER_2A, "Ch 10: Audit and Auditors", "Appointment, Powers, Duties & Rotation of Auditors"),
        SyllabusTopicNode("P2A_CH11", CAInterPaperSubject.PAPER_2A, "Ch 11: Companies Incorporated Outside India", "Companies Incorporated Outside India"),
        SyllabusTopicNode("P2A_CH12", CAInterPaperSubject.PAPER_2A, "Ch 12: The Limited Liability Partnership Act, 2008", "LLP Act, 2008 & Important Rules"),
 
        // ================= PAPER 2B: OTHER LAWS (30M) =================
        SyllabusTopicNode("P2B_CH1", CAInterPaperSubject.PAPER_2B, "Ch 1: The General Clauses Act, 1897", "Definitions, Construction & Powers"),
        SyllabusTopicNode("P2B_CH2", CAInterPaperSubject.PAPER_2B, "Ch 2: Interpretation of Statutes", "Rules of Interpretation, Aids & Construction of Deeds"),
        SyllabusTopicNode("P2B_CH3", CAInterPaperSubject.PAPER_2B, "Ch 3: The Foreign Exchange Management Act, 1999", "Current and Capital Account Transactions"),

        // ================= PAPER 3A: INCOME TAX LAW (50M) =================
        SyllabusTopicNode("P3A_CH1_BAS", CAInterPaperSubject.PAPER_3A, "Ch 1: Basics of Income Tax", "Introduction, Assessee, Person & Basis of Charge"),
        SyllabusTopicNode("P3A_CH2_RES", CAInterPaperSubject.PAPER_3A, "Ch 2: Residential Status", "Residential Status & Scope of Total Income"),
        SyllabusTopicNode("P3A_CH3_SAL", CAInterPaperSubject.PAPER_3A, "Ch 3: Salaries", "Heads of Income - Salary, Allowances & Perquisites"),
        SyllabusTopicNode("P3A_CH4_HP", CAInterPaperSubject.PAPER_3A, "Ch 4: Income from House Property", "Annual Value Computation, Deductions under Sec 24"),
        SyllabusTopicNode("P3A_CH5_PGBP", CAInterPaperSubject.PAPER_3A, "Ch 5: Profits & Gains of Business or Profession (PGBP)", "Admissible/Inadmissible Expenses, Presumptive Taxation"),
        SyllabusTopicNode("P3A_CH6_CG", CAInterPaperSubject.PAPER_3A, "Ch 6: Capital Gains", "Short-term/Long-term Gains, Exemptions under Sec 54"),
        SyllabusTopicNode("P3A_CH7_IFOS", CAInterPaperSubject.PAPER_3A, "Ch 7: Income from Other Sources", "Dividend, Gift Provisions & Casual Incomes"),
        SyllabusTopicNode("P3A_CH8_CLUB", CAInterPaperSubject.PAPER_3A, "Ch 8: Clubbing of Income", "Transfer of Income without Transfer of Assets, Minor's Income"),
        SyllabusTopicNode("P3A_CH9_SET", CAInterPaperSubject.PAPER_3A, "Ch 9: Set off & Carry Forward of Losses", "Intra-head & Inter-head Set-off, Carry Forward Rules"),
        SyllabusTopicNode("P3A_CH10_DED", CAInterPaperSubject.PAPER_3A, "Ch 10: Chapter VI-A Deductions and 10AA", "Deductions under Sec 80C to 80U & SEZ Exemption"),
        SyllabusTopicNode("P3A_CH11_TDS", CAInterPaperSubject.PAPER_3A, "Ch 11: TDS, TCS & Advance Tax", "Provisions of TDS, TCS & Advance Tax liability"),
        SyllabusTopicNode("P3A_CH12_RET", CAInterPaperSubject.PAPER_3A, "Ch 12: Return of Income", "Provisions for Filing Returns, Due Dates & Self-Assessment"),
        SyllabusTopicNode("P3A_CH13_TOT", CAInterPaperSubject.PAPER_3A, "Ch 13: Total Income and Alternate Minimum Tax (AMT)", "Computation of Total Income & Alternate Minimum Tax (AMT)"),

        // ================= PAPER 3B: GOODS AND SERVICES TAX (50M) =================
        SyllabusTopicNode("P3B_CH1_INT", CAInterPaperSubject.PAPER_3B, "Ch 1: GST in India - An Introduction", "GST Laws: Introduction, Constitutional Aspects & Genesis"),
        SyllabusTopicNode("P3B_CH2_SUP", CAInterPaperSubject.PAPER_3B, "Ch 2: Supply under GST", "Concept and Scope of Supply, Schedule I, II & III"),
        SyllabusTopicNode("P3B_CH3_CHG", CAInterPaperSubject.PAPER_3B, "Ch 3: Charge of GST", "Levy & Collection, Reverse Charge Mechanism & Composition Scheme"),
        SyllabusTopicNode("P3B_CH4_POS", CAInterPaperSubject.PAPER_3B, "Ch 4: Place of supply", "Inter-State vs Intra-State, Place of Supply of Goods & Services"),
        SyllabusTopicNode("P3B_CH5_EXM", CAInterPaperSubject.PAPER_3B, "Ch 5: Exemptions from GST", "Services and Goods Exempted from GST"),
        SyllabusTopicNode("P3B_CH6_TOS", CAInterPaperSubject.PAPER_3B, "Ch 6: Time of Supply", "Time of Supply of Goods and Services"),
        SyllabusTopicNode("P3B_CH7_VAL", CAInterPaperSubject.PAPER_3B, "Ch 7: Value of Supply", "Transaction Value, Inclusions and Exclusions"),
        SyllabusTopicNode("P3B_CH8_ITC", CAInterPaperSubject.PAPER_3B, "Ch 8: Input Tax Credit", "Eligibility, Apportionment of Credits & Blocked Credits"),
        SyllabusTopicNode("P3B_CH9_REG", CAInterPaperSubject.PAPER_3B, "Ch 9: Registration", "Persons Liable, Procedure for Registration & Cancellation"),
        SyllabusTopicNode("P3B_CH10_INV", CAInterPaperSubject.PAPER_3B, "Ch 10: Tax Invoice, Credit and Debit Notes", "Tax Invoice, Credit/Debit Notes, Delivery Challan & E-Invoicing"),
        SyllabusTopicNode("P3B_CH11_REC", CAInterPaperSubject.PAPER_3B, "Ch 11: Accounts and Records", "Maintenance of Accounts and Records, Retention Period"),
        SyllabusTopicNode("P3B_CH12_EWB", CAInterPaperSubject.PAPER_3B, "Ch 12: E-way Bill", "E-way Bill Generation, Rules, Limits and Validity"),
        SyllabusTopicNode("P3B_CH13_PAY", CAInterPaperSubject.PAPER_3B, "Ch 13: Payment of Tax", "Electronic Ledgers, Order of Utilization, Interest on Delay"),
        SyllabusTopicNode("P3B_CH14_TDS", CAInterPaperSubject.PAPER_3B, "Ch 14: Tax Deduction at Source & Collection of tax at Source", "Provisions of TDS & TCS under GST"),
        SyllabusTopicNode("P3B_CH15_RET", CAInterPaperSubject.PAPER_3B, "Ch 15: Returns", "GST Returns: Filing Procedures, GSTR-1, GSTR-3B & Annual Return"),

        // ================= PAPER 4: COST & MANAGEMENT ACCOUNTING (100M) =================
        SyllabusTopicNode("P4_CH1_INT", CAInterPaperSubject.PAPER_4, "Ch 1: Introduction to Cost and Management Accounting", "Overview of Cost & Management Accounting, Objectives, Scope and Cost Terms"),
        SyllabusTopicNode("P4_CH2_MAT", CAInterPaperSubject.PAPER_4, "Ch 2: Material Cost", "Procurement, Storage, Inventory Control, EOQ & Valuation of Materials"),
        SyllabusTopicNode("P4_CH3_EMP", CAInterPaperSubject.PAPER_4, "Ch 3: Employee Cost and Direct Expenses", "Employee Turnover, Idle Time, Incentive Schemes & Direct Expenses"),
        SyllabusTopicNode("P4_CH4_OVH", CAInterPaperSubject.PAPER_4, "Ch 4: Overheads - Absorption Costing Method", "Primary & Secondary Distribution, Under/Over Absorption, Capacity Levels"),
        SyllabusTopicNode("P4_CH5_ABC", CAInterPaperSubject.PAPER_4, "Ch 5: Activity Based Costing", "Cost Drivers, Cost Pools & Allocation of Overheads using ABC"),
        SyllabusTopicNode("P4_CH6_CSH", CAInterPaperSubject.PAPER_4, "Ch 6: Cost Sheet", "Preparation of Cost Sheets, Prime Cost, Factory Cost & Cost of Sales"),
        SyllabusTopicNode("P4_CH7_CAS", CAInterPaperSubject.PAPER_4, "Ch 7: Cost Accounting System", "Integrated & Non-Integrated Systems, Reconciliation of Cost & Financial Accounts"),
        SyllabusTopicNode("P4_CH8_UNIT", CAInterPaperSubject.PAPER_4, "Ch 8: Unit Costing and Batch Costing", "Single Output Costing, Batch Costing, Economic Batch Quantity (EBQ)"),
        SyllabusTopicNode("P4_CH9_JOB", CAInterPaperSubject.PAPER_4, "Ch 9: Job Costing", "Job Cost Cards, Allocation of Direct and Indirect Costs to Jobs"),
        SyllabusTopicNode("P4_CH10_PROC", CAInterPaperSubject.PAPER_4, "Ch 10: Process & Operation Costing", "Process Losses, Equivalent Production, Inter-Process Profit & Operation Costing"),
        SyllabusTopicNode("P4_CH11_JNT", CAInterPaperSubject.PAPER_4, "Ch 11: Joint Products and By-Products", "Apportionment of Joint Costs, Market Value Method & Treatment of By-Products"),
        SyllabusTopicNode("P4_CH12_SRV", CAInterPaperSubject.PAPER_4, "Ch 12: Service Costing", "Costing in Transport, Hospital, Hotel, IT & Infrastructure Sectors"),
        SyllabusTopicNode("P4_CH13_STD", CAInterPaperSubject.PAPER_4, "Ch 13: Standard Costing", "Standard Cost Setting, Material, Labour, and Overhead Variance Analysis"),
        SyllabusTopicNode("P4_CH14_MARG", CAInterPaperSubject.PAPER_4, "Ch 14: Marginal Costing", "Variable Costing, CVP Analysis, Break-Even Point & Decision Making"),
        SyllabusTopicNode("P4_CH15_BUD", CAInterPaperSubject.PAPER_4, "Ch 15: Budget and Budgetary Control", "Functional Budgets, Flexible Budgeting, Cash Budget, Master Budget & ZBB"),

        // ================= PAPER 5: AUDITING AND ETHICS (100M) =================
        SyllabusTopicNode("P5_CH1_NAT", CAInterPaperSubject.PAPER_5, "Ch 1: Nature, Objective and Scope of Audit", "Auditing Concepts, Qualities of Auditor & SA 200"),
        SyllabusTopicNode("P5_CH2_STR", CAInterPaperSubject.PAPER_5, "Ch 2: Audit Strategy, Audit Planning and Audit Programme", "Audit Planning, Strategy & Program (SA 300)"),
        SyllabusTopicNode("P5_CH3_RISK", CAInterPaperSubject.PAPER_5, "Ch 3: Risk Assessment and Internal Control", "Risk of Material Misstatement, IC Evaluation & SA 315"),
        SyllabusTopicNode("P5_CH3_DIG", CAInterPaperSubject.PAPER_5, "Ch 3: Risk Assessment and Internal Control", "Digital Audit, Automated Environment & SA 330"),
        SyllabusTopicNode("P5_CH4_EVID", CAInterPaperSubject.PAPER_5, "Ch 4: Audit Evidence", "Audit Evidence (SA 500), Sampling (SA 530) & Confirmations (SA 505)"),
        SyllabusTopicNode("P5_CH4_SPEC", CAInterPaperSubject.PAPER_5, "Ch 4: Audit Evidence", "Opening Balances (SA 510), Related Parties (SA 550) & Analytical (SA 520)"),
        SyllabusTopicNode("P5_CH4_INT", CAInterPaperSubject.PAPER_5, "Ch 4: Audit Evidence", "Using Work of Internal Auditors (SA 610) & Audit Trail"),
        SyllabusTopicNode("P5_CH5_FS", CAInterPaperSubject.PAPER_5, "Ch 5: Audit of Items of Financial Statements", "Audit of BS and P&L Items (Assets, Liabilities, Revenue & Expenses)"),
        SyllabusTopicNode("P5_CH6_DOC", CAInterPaperSubject.PAPER_5, "Ch 6: Audit Documentation", "Nature, Purpose & Custody of Documentation (SA 230)"),
        SyllabusTopicNode("P5_CH7_COMP", CAInterPaperSubject.PAPER_5, "Ch 7: Completion and Review", "Subsequent Events (SA 560), Going Concern (SA 570) & Written Reps (SA 580)"),
        SyllabusTopicNode("P5_CH8_REP", CAInterPaperSubject.PAPER_5, "Ch 8: Audit Report", "Forming Opinion (SA 700), KAM (SA 701), Modifications (SA 705/706) & CARO"),
        SyllabusTopicNode("P5_CH9_SPEC", CAInterPaperSubject.PAPER_5, "Ch 9: Special Features of Audit of Different Type of Entities", "Audit of Gov, Local Bodies, NPOs, Educational Institutions & LLPs"),
        SyllabusTopicNode("P5_CH10_BNK", CAInterPaperSubject.PAPER_5, "Ch 10: Audit of Banks", "Bank Audit Approach, Advances & NPA Special Consideration"),
        SyllabusTopicNode("P5_CH11_ETH", CAInterPaperSubject.PAPER_5, "Ch 11: Ethics and Terms of Audit Engagements", "Professional Ethics, Independence Threats, SQC 1, SA 210 & SA 220"),

        // ================= PAPER 6A: FINANCIAL MANAGEMENT (50M) =================
        SyllabusTopicNode("P6A_CH1_2_THEORY", CAInterPaperSubject.PAPER_6A, "Ch 1 & 2: Theory Chapters", "Scope and Sources of Finance (Long term, Short term, Lease & Contemporary)"),
        SyllabusTopicNode("P6A_CH3_RAT", CAInterPaperSubject.PAPER_6A, "Ch 3: Financial Analysis and Planning – Ratio Analysis", "Financial Analysis through Ratios"),
        SyllabusTopicNode("P6A_CH4_COC", CAInterPaperSubject.PAPER_6A, "Ch 4: Cost of Capital", "Cost of Capital (WACC & Marginal Cost of Capital)"),
        SyllabusTopicNode("P6A_CH5_CAP", CAInterPaperSubject.PAPER_6A, "Ch 5: Financing Decisions – Capital Structure", "Capital Structure Theories (EBIT-EPS Analysis, Relevancy/Irrelevancy)"),
        SyllabusTopicNode("P6A_CH6_LEV", CAInterPaperSubject.PAPER_6A, "Ch 6: Financing Decisions – Leverages", "Leverages (Operating, Financial and Combined)"),
        SyllabusTopicNode("P6A_CH7A_TVM", CAInterPaperSubject.PAPER_6A, "Ch 7A: Time Value of Money", "Concept of TVM, Compounding & Discounting Techniques"),
        SyllabusTopicNode("P6A_CH7B_INV", CAInterPaperSubject.PAPER_6A, "Ch 7B: Investment Decisions", "Capital Budgeting (Payback, ARR, NPV, IRR, MIRR, Profitability Index)"),
        SyllabusTopicNode("P6A_CH8_DIV", CAInterPaperSubject.PAPER_6A, "Ch 8: Dividend Decisions", "Dividend Decisions (Walter's, Gordon's & MM Hypothesis)"),
        SyllabusTopicNode("P6A_CH9_WC", CAInterPaperSubject.PAPER_6A, "Ch 9: Management of Working Capital", "Working Capital Cycle, Receivables, Payables, Cash & Factoring"),

        // ================= PAPER 6B: STRATEGIC MANAGEMENT (50M) =================
        SyllabusTopicNode("P6B_CH1_INT", CAInterPaperSubject.PAPER_6B, "Ch 1: Introduction to Strategic Management", "Strategic Intent (Vision, Mission, Goals) & Strategic Levels"),
        SyllabusTopicNode("P6B_CH2_EXT", CAInterPaperSubject.PAPER_6B, "Ch 2: External Environment Analysis", "PESTLE, Porter's 5 Forces & Industry Environment Analysis"),
        SyllabusTopicNode("P6B_CH3_INT", CAInterPaperSubject.PAPER_6B, "Ch 3: Internal Environment Analysis", "Mendelow's Model, SWOT & Porter's Generic Competitive Strategies"),
        SyllabusTopicNode("P6B_CH4_CHC", CAInterPaperSubject.PAPER_6B, "Ch 4: Strategic Choices", "Ansoff, BCG, ADL, GE Matrix, Turnaround, Divestiture & Liquidation"),
        SyllabusTopicNode("P6B_CH5_IMP", CAInterPaperSubject.PAPER_6B, "Ch 5: Strategy Implementation & Evaluation", "Digital Transformation, Org Culture, Leadership & Performance Controls")
    )

    fun getChaptersForSubject(subject: CAInterPaperSubject): List<String> {
        return allTopics.filter { it.subject == subject }.map { it.chapterName }.distinct()
    }

    fun getSubTopicsForChapter(subject: CAInterPaperSubject, chapterName: String): List<SyllabusTopicNode> {
        return allTopics.filter { it.subject == subject && it.chapterName == chapterName }
    }
}
