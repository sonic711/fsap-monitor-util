CREATE OR REPLACE VIEW v_prod_eureka_registry_info AS 
SELECT
* 
FROM read_json_auto('00_info/prod_eureka_registry_info.jsonl');
