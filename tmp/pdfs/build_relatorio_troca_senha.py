from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (
    SimpleDocTemplate,
    KeepTogether,
    PageBreak,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)


OUTPUT = Path("output/pdf/relatorio_troca_senha_zoe.pdf")
USER_SUB = "a4a8d478-4031-7043-c3e1-0b92209d332b"
USER_EMAIL = "zoe@sacs.com"
COGNITO_URL = (
    "https://us-east-1.console.aws.amazon.com/cognito/v2/idp/user-pools/"
    "us-east-1_p3X8vXclQ/user-management/users/details/"
    "a4a8d478-4031-7043-c3e1-0b92209d332b?region=us-east-1"
)


def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#CBD5E1"))
    canvas.line(doc.leftMargin, 1.35 * cm, A4[0] - doc.rightMargin, 1.35 * cm)
    canvas.setFillColor(colors.HexColor("#475569"))
    canvas.setFont("Helvetica", 8)
    canvas.drawString(doc.leftMargin, 0.85 * cm, "Relatorio de troca de senha")
    canvas.drawRightString(A4[0] - doc.rightMargin, 0.85 * cm, f"Pagina {doc.page}")
    canvas.restoreState()


styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="TitleCustom", parent=styles["Title"], fontName="Helvetica-Bold",
    fontSize=22, leading=27, textColor=colors.HexColor("#0F172A"), spaceAfter=10,
))
styles.add(ParagraphStyle(
    name="Subtitle", parent=styles["Normal"], fontName="Helvetica",
    fontSize=11, leading=16, textColor=colors.HexColor("#475569"), spaceAfter=18,
))
styles.add(ParagraphStyle(
    name="HeadingCustom", parent=styles["Heading2"], fontName="Helvetica-Bold",
    fontSize=14, leading=18, textColor=colors.HexColor("#0F172A"),
    spaceBefore=12, spaceAfter=7,
))
styles.add(ParagraphStyle(
    name="BodyCustom", parent=styles["BodyText"], fontName="Helvetica",
    fontSize=10, leading=15, textColor=colors.HexColor("#1E293B"), spaceAfter=7,
))
styles.add(ParagraphStyle(
    name="Small", parent=styles["BodyText"], fontName="Helvetica",
    fontSize=8.2, leading=11, textColor=colors.HexColor("#334155"),
))
styles.add(ParagraphStyle(
    name="Conclusion", parent=styles["BodyText"], fontName="Helvetica-Bold",
    fontSize=11, leading=16, textColor=colors.HexColor("#0F172A"),
))


def p(text, style="BodyCustom"):
    return Paragraph(text, styles[style])


def event_table(rows):
    data = [[p("Horario", "Small"), p("Evento", "Small"), p("O que registra", "Small")]]
    for hour, event, detail in rows:
        data.append([p(hour, "Small"), p(event, "Small"), p(detail, "Small")])
    table = Table(data, colWidths=[2.25 * cm, 4.0 * cm, 10.35 * cm], repeatRows=1)
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0F4C5C")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CBD5E1")),
        ("BACKGROUND", (0, 1), (-1, -1), colors.HexColor("#F8FAFC")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#F8FAFC"), colors.white]),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return table


def fact_table(rows):
    data = [[p(label, "Small"), p(value, "Small")] for label, value in rows]
    table = Table(data, colWidths=[4.45 * cm, 12.15 * cm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#E2E8F0")),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CBD5E1")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return table


doc = SimpleDocTemplate(
    str(OUTPUT), pagesize=A4, rightMargin=1.45 * cm, leftMargin=1.45 * cm,
    topMargin=1.45 * cm, bottomMargin=1.85 * cm,
)

story = []
story.append(p("Relatorio de troca de senha", "TitleCustom"))
story.append(p("Conta Cognito: zoe - analise dos registros AWS CloudTrail", "Subtitle"))
story.append(fact_table([
    ("Usuario", "zoe"),
    ("E-mail", USER_EMAIL),
    ("Identificador Cognito (sub)", USER_SUB),
    ("Data da ocorrencia", "11/08/2026"),
    ("Momento da troca efetiva", "12:58:53, horario de Brasilia (15:58:53 UTC)"),
]))

story.append(p("Resumo para leitura simples", "HeadingCustom"))
story.append(p(
    "Os registros da AWS mostram que o sistema criou uma senha temporaria para a conta "
    "zoe. Depois, a conta tentou entrar e recebeu a mensagem de que a senha temporaria "
    "precisava ser trocada. As 12:58:53, a AWS registrou que essa exigencia foi concluida "
    "sem erro e liberou o acesso."
))
story.append(p(
    "Portanto, a senha temporaria da conta <b>zoe@sacs.com</b> foi substituida por uma "
    "nova senha em <b>11/08/2026 as 12:58:53, horario de Brasilia</b>.", "Conclusion"))

story.append(p("Linha do tempo", "HeadingCustom"))
story.append(event_table([
    ("12:54:37", "AdminSetUserPassword", "O sistema definiu senha temporaria. O registro informa permanent=false, isto e, a senha nao era definitiva."),
    ("12:55:27", "AdminInitiateAuth", "A conta iniciou login. A AWS respondeu NEW_PASSWORD_REQUIRED: a senha temporaria precisava ser substituida."),
    ("12:57:35", "AdminInitiateAuth", "Nova tentativa de login. A exigencia NEW_PASSWORD_REQUIRED continuou ativa."),
    ("12:58:53", "AdminRespondToAuthChallenge", "A conta concluiu a troca obrigatoria de senha. A AWS gerou os tokens de acesso e nao registrou erro."),
    ("12:58:55", "InitiateAuth", "Renovacao de sessao concluida para mesma conta, confirmando autenticacao valida apos a troca."),
]))

story.append(p("Conclusao tecnica", "HeadingCustom"))
story.append(p(
    "A sequencia cronologica e consistente: senha temporaria criada, obrigacao de troca apresentada, "
    "troca obrigatoria de senha concluida e sessao autenticada. Isso comprova a troca da senha temporaria na conta Cognito identificada por "
    f"<b>{USER_SUB}</b>. Os eventos analisados foram bem-sucedidos e nao apresentam erro.",
    "Conclusion"
))
story.append(PageBreak())
story.append(p("O que cada evento significa", "TitleCustom"))
story.append(p(
    "<b>Importante sobre o nome dos eventos:</b> quando um evento comeca por Admin, a palavra indica que o backend usou uma API do Cognito com permissao administrativa. "
    "Ela nao identifica uma pessoa administradora, nao significa que a senha de um administrador foi alterada e nao afirma que alguem em papel de administrador digitou a nova senha. "
    f"A conta tratada nos eventos e identificada pelo sub=<b>{USER_SUB}</b>, correspondente a <b>{USER_EMAIL}</b>."
))
story.append(p(
    "<b>AdminSetUserPassword</b> - acao administrativa feita pelo sistema para definir ou redefinir a senha de uma conta. "
    "Neste caso, o registro permanent=false mostra que foi criada uma senha temporaria. Essa senha serve somente para o primeiro acesso e exige a definicao de uma nova senha."
))
story.append(p(
    "<b>AdminInitiateAuth</b> - inicio de uma tentativa de login feita pelo backend em nome da conta. Esse evento verifica os dados de acesso e informa qual e o proximo passo. "
    "Ele apareceu duas vezes porque houve duas tentativas. Em ambas, Cognito retornou NEW_PASSWORD_REQUIRED: a conta foi reconhecida, mas a troca da senha temporaria ainda era obrigatoria."
))
story.append(p(
    "<b>AdminRespondToAuthChallenge</b> - conclusao da etapa que Cognito exige antes de liberar o acesso. Neste caso, a etapa era NEW_PASSWORD_REQUIRED, ou seja, a troca da senha temporaria por uma nova senha. "
    "Como o registro nao tem erro e retorna authenticationResult, a nova senha foi aceita e a conta foi autenticada. O prefixo Admin aqui e apenas o nome da API usada pelo backend; o evento esta vinculado ao sub de zoe, nao a uma conta de administrador."
))
story.append(p(
    "<b>InitiateAuth</b> - inicio de autenticacao sem permissao administrativa. No registro analisado, o fluxo foi REFRESH_TOKEN_AUTH: o sistema usou o token de renovacao da sessao ja criada para obter novos tokens. "
    "Esse evento confirma que a sessao ficou valida depois da troca."
))

story.append(PageBreak())
story.append(p("Provas detalhadas", "TitleCustom"))
story.append(p("Esta secao registra os elementos tecnicos que sustentam a conclusao anterior.", "Subtitle"))

story.append(p("1. Vinculo da conta analisada", "HeadingCustom"))
story.append(p(
    "Os eventos da troca contem em AdditionalEventData o valor sub="
    f"<b>{USER_SUB}</b>. Esse identificador e unico no Cognito e corresponde a conta "
    f"<b>{USER_EMAIL}</b>."
))
story.append(p("2. Criação da senha temporaria", "HeadingCustom"))
story.append(fact_table([
    ("EventId", "426a86f9-6747-485f-b54c-aebe0c97835e"),
    ("Horario", "2026-08-11T15:54:37Z - 12:54:37 BRT"),
    ("Acao", "AdminSetUserPassword"),
    ("Registro principal", "permanent=false"),
    ("Resultado", "Sem erro"),
    ("Conta vinculada", f"sub={USER_SUB}"),
]))
story.append(p(
    "O parametro permanent=false significa que a senha criada era temporaria. O proximo login deveria obrigatoriamente definir uma nova senha."
))

story.append(p("3. Confirmação da obrigacao de trocar senha", "HeadingCustom"))
story.append(fact_table([
    ("EventIds", "7a50abbc-5d01-402c-9357-9d2e408291b0 e 1b32f38f-9ec3-402b-b993-33315101bfc0"),
    ("Horarios", "12:55:27 BRT e 12:57:35 BRT"),
    ("Acao", "AdminInitiateAuth"),
    ("Resposta Cognito", "challengeName=NEW_PASSWORD_REQUIRED"),
    ("Conta vinculada", f"sub={USER_SUB}"),
]))
story.append(p(
    "A resposta NEW_PASSWORD_REQUIRED nao representa login concluido. Ela informa que Cognito reconheceu a conta, mas bloqueou a autenticacao normal ate que uma nova senha fosse fornecida."
))

story.append(PageBreak())
story.append(p("Provas detalhadas - continuacao", "HeadingCustom"))
story.append(p("4. Prova da troca efetiva", "HeadingCustom"))
story.append(fact_table([
    ("EventId", "15796f96-57dd-4f6f-abf6-3d13397c990c"),
    ("Horario", "2026-08-11T15:58:53Z - 12:58:53 BRT"),
    ("Acao", "AdminRespondToAuthChallenge"),
    ("Troca de senha", "challengeName=NEW_PASSWORD_REQUIRED"),
    ("Resultado", "authenticationResult emitido; sem errorCode ou errorMessage"),
    ("Conta vinculada", f"sub={USER_SUB}"),
]))
story.append(p(
    "Esse e o registro decisivo. Cognito somente retorna authenticationResult depois de aceitar a conclusao da troca obrigatoria de senha. "
    "Nesse fluxo, concluir com sucesso NEW_PASSWORD_REQUIRED exige definir uma nova senha; por isso, o horario do evento "
    "marca a troca efetiva da senha temporaria."
))

story.append(p("5. Confirmação posterior de sessão", "HeadingCustom"))
story.append(fact_table([
    ("EventId", "dbdb100d-a850-400a-8dd3-4bcc1c706b67"),
    ("Horario", "2026-08-11T15:58:55Z - 12:58:55 BRT"),
    ("Acao", "InitiateAuth"),
    ("Fluxo", "REFRESH_TOKEN_AUTH"),
    ("Resultado", "accessToken e idToken emitidos; sem erro"),
    ("Conta vinculada", f"sub={USER_SUB}"),
]))
story.append(p(
    "Dois segundos apos a conclusao da troca de senha, Cognito registrou renovacao de sessao com sucesso para a mesma conta. "
    "Esse fato e compativel com a autenticacao criada logo apos a nova senha ser aceita."
))

doc.build(story, onFirstPage=footer, onLaterPages=footer)
print(OUTPUT.resolve())
