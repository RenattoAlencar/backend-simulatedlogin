# backend-simulatedlogin
Aqui está a documentação completa em Jira wiki markup, cobrindo o que foi feito nesta sessão e como validar cada parte. Cobre desde a arquitetura modular até o estado atual da integração LexisNexis, com os passos de validação reproduzíveis. Os pontos para print estão marcados com *[PRINT: ...]*.
{code}

h1. Arquitetura de Journeys Modulares + Integração LexisNexis (PingOne Advanced Identity Cloud)
h2. 1. Objetivo
Estruturar a autenticação de clientes no PingOne Advanced Identity Cloud (Encore) com separação de responsabilidades: journeys de login por canal que compõem journeys de segurança reutilizáveis (LexisNexis, AuthFy/OTP) via Inner Tree Evaluator. Etapa atual: arquitetura modular validada e início da integração LexisNexis (DDP Session Query) via Scripted Decision.
h2. 2. Ambiente
|| Item || Valor ||

| Plataforma | PingOne Advanced Identity Cloud (Encore) |

| Tenant | portoseg.encore.pingidentity.com |

| Realm | /Sandbox |

| Usuário de teste | renatoalencar (renato.alencar@portoseguro.com.br) |

| Client OAuth | psg-authcorp-mobile-lab (Public + PKCE) |
h2. 3. Arquitetura de journeys
h3. 3.1. Modelo de composição
Separação em três camadas:
|| Camada || Journeys || Papel ||

| Login por canal | Login-Lab-v2 (modelo) | Entrada do canal, composição customizada |

| Segurança reutilizável | Login-Lab-Security, Sec-LexisNexis | Inner journeys, blocos de segurança |

| Encadeamento | Inner Tree Evaluator | Compõe os blocos na ordem que o canal exige |
Modelo alvo (3 canais):

{code}

Canal 1 → Login-Canal1 → [Sec-LexisNexis] → [Sec-AuthFy-OTP] → Success

Canal 2 → Login-Canal2 → [Sec-LexisNexis] → Success

Canal 3 → Login-Canal3 → [Sec-OutroServico] → Success

{code}
h3. 3.2. Journey principal (Login-Lab-v2)
{code}

Start → [Page Node: Platform Username + Platform Password] → Data Store Decision

Data Store Decision (True)  → [Inner Tree Evaluator] → True → Success

└ False → Failure

Data Store Decision (False) → Failure

{code}
Configuração da journey:

Identity Object: User (managed/user)
Run journey for all users regardless of current session: habilitado
Inner Journey: desabilitado (journey principal, chamável direto)

[PRINT: canvas da Login-Lab-v2]
h3. 3.3. Inner journey de segurança
Configuração (aplica-se a Login-Lab-Security, Sec-LexisNexis e demais):

Identity Object: User (managed/user)
Inner Journey: habilitado (chamável apenas via Inner Tree Evaluator)
Run journey for all users regardless of current session: habilitado

[PRINT: canvas da inner journey de segurança]
h2. 4. Pontos críticos identificados
(!) Lições da implementação que impactam reprodutibilidade:
|| Item || Observação ||

| Inner journey pulada | Inner journeys são puladas se o usuário já tem sessão válida. Habilitar "Run journey for all users regardless of current session" em TODAS as journeys envolvidas. |

| Journey corrompida / cache | A journey original (Login-Lab) parou de propagar o resultado do Inner Tree Evaluator. A recriação do zero (Login-Lab-v2) resolveu. Em caso de comportamento inconsistente, recriar a journey. |

| Usuário de teste | Testes devem usar o end user (renatoalencar), NUNCA o amadmin. O amadmin tem caminho de autenticação privilegiado e não reflete o fluxo real. |

| Inner Tree Evaluator não é chamável direto | Inner journeys retornam "No Configuration found" se acionadas via authIndexValue. É o comportamento esperado. |

| Nós LexisNexis nativos ausentes | Os nós ThreatMetrix nativos não estão instalados no tenant. Integração feita via Scripted Decision + httpClient. |
h2. 5. Integração LexisNexis (Sec-LexisNexis)
h3. 5.1. Abordagem
Sem nós nativos ThreatMetrix no tenant. A chamada à LexisNexis DDP Session Query é feita por um Scripted Decision Node (Next Generation) usando o binding httpClient.
h3. 5.2. Contrato da API (DDP Session Query)

Método: POST
URL: https://h-api.online-metrix.net/api/session-query
Headers: Content-Type e Accept, ambos application/json

Campos do request:

|| Campo || Origem || Observação ||

| org_id | ESV (credencial) | |

| api_key | ESV (credencial) | |

| event_type | fixo | "LOGIN" |

| session_id | dinâmico | gerado pelo Profiler (device) |

| policy | varia por canal | ex.: "login_app" |

| customer_event_type | varia por canal | ex.: "superapp" |

| account_login | dinâmico | username |

| account_email | dinâmico | perfil do usuário |

| account_email_domain | derivado | domínio do account_email |

| service_type | fixo | "all" |

| output_format | fixo | "JSON" |

| national_id_type | fixo | "BR_CPF" |
Campos relevantes do response:

|| Campo || Uso ||

| review_status | Veredito principal (pass / reject / review) — define o roteamento |

| risk_rating | Classificação de risco (ex.: trusted) — auditoria |

| rules | Reason codes disparados — auditoria, futuro step-up |
(i) O caminho de parsing do review_status na resposta (objeto aninhado) deve ser confirmado contra o JSON real antes de ativar o roteamento.
h3. 5.3. Script (Scripted Decision, Next Generation)
Localização: Scripts > Auth Scripts > Journey Decision Node > Next Generation.
{code:javascript}

// Credenciais (ESV)

var orgId  = systemEnv.getProperty("esv-lexis-org-id");

var apiKey = systemEnv.getProperty("esv-lexis-api-key");
// Config por canal (injetada pela journey de canal)

var policy        = nodeState.get("lexis_policy");

var customerEvent = nodeState.get("lexis_customer_event");
// Dados do usuário

var sessionId   = nodeState.get("lexis_session_id");

var login       = nodeState.get("username");

var email       = nodeState.get("account_email");

var emailDomain = (email != null && email.indexOf("@") > -1)

? email.substring(email.indexOf("@") + 1) : "";
var payload = {

"org_id": orgId, "event_type": "LOGIN", "session_id": sessionId,

"policy": policy, "account_login": login, "account_email": email,

"customer_event_type": customerEvent, "api_key": apiKey,

"service_type": "all", "output_format": "JSON",

"account_email_domain": emailDomain, "national_id_type": "BR_CPF"

};
var requestOptions = {

"clientName": "lexisHttpClient",

"method": "POST",

"headers": { "Content-Type": "application/json", "Accept": "application/json" },

"body": JSON.stringify(payload)

};
try {

var res = httpClient.send("https://h-api.online-metrix.net/api/session-query", requestOptions).get();

if (res.status == 200) {

var data = JSON.parse(res.text());

var review = data.review_status; // AJUSTAR caminho conforme JSON real

nodeState.putShared("lexis_review_status", review);

if (review === "pass")        { action.goTo("pass"); }

else if (review === "reject") { action.goTo("reject"); }

else                          { action.goTo("review"); }

} else {

logger.error("LexisNexis HTTP " + res.status + ": " + res.text());

action.goTo("error");

}

} catch (e) {

logger.error("LexisNexis exception: " + e);

action.goTo("error");

}

{code}
Outcomes a configurar no nó: pass, reject, review, error.

Ligações: pass → próximo bloco/Success; reject/review/error → Failure.
h3. 5.4. Pré-requisitos de plataforma (pendentes)
(!) Necessários para a chamada real funcionar (configuração de tenant):

HTTP Client service nomeado "lexisHttpClient" configurado.
ESVs criadas: esv-lexis-org-id, esv-lexis-api-key.
Domínio h-api.online-metrix.net liberado na rede de saída do tenant.

h2. 6. Como validar
h3. 6.1. Validar o encadeamento (Login → Inner de segurança)
Método do MOCK + toggle (independe da plataforma LexisNexis):
Inner journey com Scripted Decision MOCK: grava lexis_review_status=mock-pass e segue para "pass".
Login-Lab-v2 com Inner Tree Evaluator apontando para a inner.
Aba anônima, debug habilitado, autenticar com renatoalencar via:
{code}

https://portoseg.encore.pingidentity.com/am/XUI/?realm=/Sandbox&authIndexType=service&authIndexValue=Login-Lab-v2#/

{code}
No debug, confirmar steps adicionais após o Data Store Decision (entrada na inner journey).
Teste de controle (prova definitiva):
Inner journey em Start → Failure. Salvar. Salvar Login-Lab-v2.
Autenticar com credenciais válidas (renatoalencar).
Resultado esperado: "Login failure" na tela — comprova que a inner controla o fluxo.
Reverter a inner para o caminho de aprovação.
[PRINT: tela de Login failure com inner em Failure]

[PRINT: debug mostrando steps da inner journey]
h3. 6.2. Validar a chamada LexisNexis (após pré-requisitos)
Substituir o MOCK pelo script real (seção 5.3).
Configurar HTTP Client + ESVs.
Autenticar com renatoalencar e inspecionar o debug:
** lexis_review_status presente no sharedState.

** Roteamento correto conforme review_status retornado.
Validar caminho de parsing contra o JSON real da DDP.
h2. 7. Status
|| Item || Status ||

| Arquitetura modular (Login → Inner via Inner Tree Evaluator) | Validado |

| Teste de controle (inner reprova → login falha) | Validado |

| Recriação Login-Lab-v2 (correção de journey corrompida) | Concluído |

| Sec-LexisNexis criada (inner journey) | Concluído |

| Script Scripted Decision (MOCK) | Em andamento |

| Pré-requisitos de plataforma (HTTP Client + ESV) | Pendente |

| Chamada LexisNexis real | Pendente |
h2. 8. Próximos passos

Finalizar criação do Scripted Decision MOCK e validar encadeamento da Sec-LexisNexis.
Provisionar HTTP Client service + ESVs no tenant.
Confirmar caminho de parsing do review_status no JSON de resposta da DDP.
Ativar a chamada real e validar roteamento.
Implementar config por canal (policy, customer_event_type) — Login-Canal1/2/3.
Integração AuthFy/OTP (MuleSoft) via Scripted Decision + httpClient (fase seguinte).

{code}

Dois ajustes que você provavelmente vai querer ao colar:

Substituir os *[PRINT: ...]* pelos screenshots — deixei nos 4 pontos mais úteis (canvas das journeys, tela de Login failure, debug com steps da inner).
As macros de aviso (!) e (i) funcionam no Confluence; no Jira nativo podem não renderizar. Se ficar estranho, troca por *Atenção:* em negrito. O resto (h2., {code}, ||) cola limpo no Jira.
