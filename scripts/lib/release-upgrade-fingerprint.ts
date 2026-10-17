/** Release-test projections. Production code never reads the released schema. */
export interface PreservedPeopleIds {
	accounts: readonly string[];
	identities: readonly string[];
}

export type UpgradeSchema = "released" | "candidate";

function ids(values: readonly string[]): string {
	if (values.some((value) => !/^[1-9][0-9]*$/u.test(value))) {
		throw new TypeError("Preserved people ids must be positive decimal strings");
	}
	return values.length === 0 ? "NULL" : values.join(",");
}

/** Existing accounts/identity ids must survive; new unclaimed people may be added. */
export function preservationSql(schema: UpgradeSchema, preserved?: PreservedPeopleIds): string {
	if (schema === "candidate" && preserved === undefined) {
		throw new TypeError("Candidate comparison requires the released people ids");
	}
	const accounts = preserved === undefined ? "" : ` WHERE id IN (${ids(preserved.accounts)})`;
	const identities = preserved === undefined ? "" : ` WHERE id IN (${ids(preserved.identities)})`;
	const identity =
		schema === "released"
			? `SELECT 'identity', concat_ws('|', id, account_id, provider_id, subject,
			coalesce(team_id,''), linked_at, last_login_at, disabled_at,
			CASE linked_via WHEN 'OAUTH_LOGIN' THEN 'SIGN_IN' WHEN 'MANUAL_LINK' THEN 'MANUAL' END)
			FROM identity_link${identities}`
			: `SELECT 'identity', concat_ws('|', id, account_id, provider_id, provider_user_key,
			team_key, linked_at, last_login_at, disabled_at, linked_via)
			FROM identity${identities}`;
	const memberships =
		schema === "released"
			? `SELECT 'membership', concat_ws('|', m.workspace_id, u.provider_id, u.native_id,
			m.role, m.hidden, 'ACTIVE')
			FROM workspace_membership m JOIN "user" u ON u.id=m.user_id WHERE upper(u.type)='USER'`
			: `SELECT DISTINCT 'membership', concat_ws('|', m.workspace_id, u.provider_id, u.native_id,
			m.role, m.hidden, 'ACTIVE')
			FROM membership m JOIN identity i ON i.account_id=m.account_id AND i.team_key=''
			JOIN "user" u ON u.provider_id=i.provider_id AND u.native_id::text=i.provider_user_key
			WHERE m.left_at IS NULL AND upper(u.type)='USER'`;
	return `SELECT kind || '|' || value FROM (
	 SELECT 'account' AS kind, concat_ws('|', id, display_name, app_role, status) AS value FROM account${accounts}
	 UNION ALL ${identity}
	 UNION ALL SELECT 'user', concat_ws('|', id, provider_id, native_id, login, type) FROM "user"
	 UNION ALL SELECT 'workspace', concat_ws('|', w.id,
	  coalesce(to_jsonb(w)->>'workspace_slug',to_jsonb(w)->>'slug'),
	  w.display_name,w.account_login,w.account_type,w.status) FROM workspace w
	 UNION ALL ${memberships}
	 UNION ALL SELECT 'connection', concat_ws('|',id,workspace_id,kind,state) FROM connection
	 ) fixture ORDER BY kind,value;`;
}
