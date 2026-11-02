export type Division = 'GARHWAL' | 'KUMAON';
export interface DistrictInfo {
  code: string; label: string; division: Division; col: number; row: number; blurb: string;
}

/** Tile-map positions (col,row) roughly follow real geography, north at the top. */
export const DISTRICT_LIST: DistrictInfo[] = [
  { code: 'UTTARKASHI', label: 'Uttarkashi', division: 'GARHWAL', col: 0, row: 0, blurb: 'Gangotri, Yamunotri and the Dayara Bugyal meadows.' },
  { code: 'CHAMOLI', label: 'Chamoli', division: 'GARHWAL', col: 1, row: 0, blurb: 'Badrinath, Valley of Flowers and Auli.' },
  { code: 'PITHORAGARH', label: 'Pithoragarh', division: 'KUMAON', col: 3, row: 0, blurb: 'Munsiyari and the Kailash–Mansarovar route.' },
  { code: 'TEHRI_GARHWAL', label: 'Tehri Garhwal', division: 'GARHWAL', col: 0, row: 1, blurb: 'Tehri lake, Devprayag and the Bhagirathi valley.' },
  { code: 'RUDRAPRAYAG', label: 'Rudraprayag', division: 'GARHWAL', col: 1, row: 1, blurb: 'Kedarnath, Tungnath–Chopta and Ukhimath.' },
  { code: 'BAGESHWAR', label: 'Bageshwar', division: 'KUMAON', col: 2, row: 1, blurb: 'Bagnath temple, Kausani and Baijnath.' },
  { code: 'CHAMPAWAT', label: 'Champawat', division: 'KUMAON', col: 3, row: 1, blurb: 'Purnagiri temple and the old Chand-dynasty capital.' },
  { code: 'DEHRADUN', label: 'Dehradun', division: 'GARHWAL', col: 0, row: 2, blurb: 'The state capital in the Doon valley; education and services hub.' },
  { code: 'PAURI_GARHWAL', label: 'Pauri Garhwal', division: 'GARHWAL', col: 1, row: 2, blurb: 'The Garhwali heartland: Srinagar, Khirsu and Lansdowne.' },
  { code: 'ALMORA', label: 'Almora', division: 'KUMAON', col: 2, row: 2, blurb: "Kumaon's cultural capital: Jageshwar and bal mithai." },
  { code: 'HARIDWAR', label: 'Haridwar', division: 'GARHWAL', col: 0, row: 3, blurb: 'Gateway of the Ganga, Har ki Pauri and the SIDCUL industrial belt.' },
  { code: 'NAINITAL', label: 'Nainital', division: 'KUMAON', col: 2, row: 3, blurb: 'Lake town: Bhimtal, Mukteshwar and the Haldwani gateway.' },
  { code: 'UDHAM_SINGH_NAGAR', label: 'Udham Singh Nagar', division: 'KUMAON', col: 3, row: 3, blurb: 'Terai farmland: Rudrapur, Kashipur and Kichha.' },
];

export const DISTRICTS = DISTRICT_LIST.map((d) => d.code) as [string, ...string[]];
export const districtInfo = (code?: string | null) => DISTRICT_LIST.find((d) => d.code === code);
export const districtLabel = (code?: string | null) => districtInfo(code)?.label ?? '';

export const COMMUNITY_OPTIONS = [
  { value: 'GARHWALI', label: 'Garhwali' }, { value: 'KUMAONI', label: 'Kumaoni' },
  { value: 'JAUNSARI', label: 'Jaunsari' }, { value: 'BHOTIYA', label: 'Bhotiya' },
  { value: 'RAJPUT', label: 'Rajput' }, { value: 'BRAHMIN', label: 'Brahmin' },
  { value: 'SCHEDULED', label: 'Scheduled community' }, { value: 'OTHER_PAHADI', label: 'Other Pahadi community' },
] as const;
export const COMMUNITIES = COMMUNITY_OPTIONS.map((c) => c.value) as [string, ...string[]];
export const communityLabel = (v?: string | null) => COMMUNITY_OPTIONS.find((c) => c.value === v)?.label ?? '';

export const GENDER_OPTIONS = [
  { value: 'FEMALE', label: 'Bride (Female)' }, { value: 'MALE', label: 'Groom (Male)' }, { value: 'OTHER', label: 'Other' },
] as const;

export const DIVISION_LABEL: Record<Division, string> = { GARHWAL: 'Garhwal', KUMAON: 'Kumaon' };
