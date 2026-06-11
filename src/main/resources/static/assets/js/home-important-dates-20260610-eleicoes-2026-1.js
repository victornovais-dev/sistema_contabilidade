(() => {
  const listElement = document.querySelector("[data-important-dates-list]");
  const emptyElement = document.querySelector("[data-important-dates-empty]");

  if (!listElement) {
    return;
  }

  const IMPORTANT_DATES = [
    {
      date: "2026-06-01",
      audience: "AGENTES PUBLICOS",
      description:
        "Prazo para liberacao do Fundo Eleitoral para o TSE pela Uniao.",
    },
    {
      date: "2026-06-01",
      audience: "PARTIDOS",
      description:
        "Prazo para que partidos comuniquem ao TSE a renuncia ao Fundo Eleitoral.",
    },
    {
      date: "2026-06-16",
      audience: "JUSTICA",
      description:
        "Prazo para divulgar o montante de recursos disponiveis no Fundo Especial de Financiamento de Campanha (FEFC).",
    },
    {
      date: "2026-07-04",
      audience: "CANDIDATOS",
      description:
        "Inicio do periodo em que e proibido participar de inauguracoes de obras publicas.",
    },
    {
      date: "2026-07-05",
      audience: "FEDERACOES",
      description:
        "Prazo para informar partidos autorizados a usar sistema CANDex.",
    },
    {
      date: "2026-07-05",
      audience: "CANDIDATOS",
      description:
        "Inicio da propaganda intrapartidaria para escolha de candidatos.",
    },
    {
      date: "2026-07-20",
      audience: "PARTIDOS",
      description:
        "Inicio das convencoes partidarias para escolha de candidatos (ate 05 de agosto).",
    },
    {
      date: "2026-07-20",
      audience: "PARTIDOS",
      description:
        "Inicio do envio das atas de convencoes partidarias a Justica Eleitoral.",
    },
    {
      date: "2026-07-20",
      audience: "JUSTICA",
      description:
        "Inicio do envio de pedidos de CNPJ da campanha a Receita Federal.",
    },
    {
      date: "2026-07-20",
      audience: "JUSTICA",
      description: "Prazo para TSE publicar limites de gastos de campanha.",
    },
    {
      date: "2026-07-20",
      audience: "PARTIDOS E CANDIDATOS",
      description:
        "Inicio da obrigacao de informar recursos financeiros recebidos nas campanhas.",
    },
    {
      date: "2026-07-20",
      audience: "PARTIDOS E CANDIDATOS",
      description:
        "Inicio do prazo para firmar contratos de preparacao de campanha e instalacao de comites.",
    },
    {
      date: "2026-07-20",
      audience: "PROVEDORES DE INTERNET",
      description:
        "Prazo para apresentar informacoes para prestacao de servicos de impulsionamento de propaganda eleitoral.",
    },
    {
      date: "2026-08-04",
      audience: "CANDIDATOS",
      description: "Prazo para propaganda intrapartidaria.",
    },
    {
      date: "2026-08-05",
      audience: "PARTIDO",
      description: "Prazo final para convencoes partidarias.",
    },
    {
      date: "2026-08-15",
      audience: "PARTIDO, COLIGACAO E FEDERACAO",
      description: "Prazo final para registro de candidaturas.",
    },
    {
      date: "2026-08-16",
      audience: "PARTIDO E CANDIDATO",
      description: "Inicio oficial da propaganda eleitoral.",
    },
    {
      date: "2026-08-16",
      audience: "CANDIDATO",
      description: "Lives passam a ser consideradas atos de campanha.",
    },
    {
      date: "2026-08-16",
      audience: "PARTIDO, COLIGACAO, FEDERACAO E CANDIDATO",
      description:
        "Inicio da permissao para uso de alto-falantes e amplificadores em campanha (ate 3 de outubro).",
    },
    {
      date: "2026-08-16",
      audience: "PARTIDO, COLIGACAO, FEDERACAO E CANDIDATO",
      description: "Inicio da realizacao de comicios (ate 01 de outubro).",
    },
    {
      date: "2026-08-16",
      audience: "PARTIDO, COLIGACAO, FEDERACAO E CANDIDATO",
      description:
        "Inicio de carreatas, caminhadas e distribuicao de material de campanha (ate 03 de outubro).",
    },
    {
      date: "2026-08-16",
      audience: "PARTIDO, COLIGACAO, FEDERACAO E CANDIDATO",
      description:
        "Inicio da propaganda eleitoral paga em jornais (ate 02 de outubro).",
    },
    {
      date: "2026-08-16",
      audience: "PARTIDO, COLIGACAO, FEDERACAO E CANDIDATO",
      description:
        "Inicio do impulsionamento pago de propaganda eleitoral na internet (ate 01 de outubro).",
    },
    {
      date: "2026-08-16",
      audience: "GERAL",
      description: "Inicio da proibicao de enquetes eleitorais.",
    },
    {
      date: "2026-08-30",
      audience: "PARTIDO",
      description: "Prazo para distribuicao de recursos do fundo eleitoral (FEFC).",
    },
    {
      date: "2026-09-09",
      audience: "PARTIDO E CANDIDATO",
      description: "Inicio do envio da prestacao parcial de contas (ate 13 de setembro).",
    },
    {
      date: "2026-09-19",
      audience: "CANDIDATO",
      description: "Inicio da proibicao de prisao (ate 06 de outubro).",
    },
    {
      date: "2026-10-01",
      audience: "PARTIDO, COLIGACAO, FEDERACAO E CANDIDATO",
      description:
        "Inicio da proibicao de conteudo eleitoral gerado por IA (ate 24 horas depois do fim das votacoes).",
    },
    {
      date: "2026-10-02",
      audience: "PARTIDO E CANDIDATO",
      description: "Prazo final para propaganda eleitoral paga em jornais.",
    },
    {
      date: "2026-10-05",
      audience: "PARTIDO E CANDIDATO",
      description:
        "Inicio da prestacao de contas do primeiro turno e inicio da campanha do 2o turno.",
    },
    {
      date: "2026-11-03",
      audience: "PARTIDO E CANDIDATO",
      description:
        "Ultimo dia de enviar a Prestacao de Contas Final ao TSE referente ao 1o turno.",
    },
    {
      date: "2026-11-14",
      audience: "PARTIDO E CANDIDATO",
      description:
        "Ultimo dia de enviar a Prestacao de Contas Final ao TSE referente ao 2o turno.",
    },
    {
      date: "2026-12-31",
      audience: "BANCOS",
      description:
        "Caso os candidatos nao tenham encerrado suas contas correntes, o banco ira encerrar as contas e o saldo positivo que houver sera enviado para a Uniao.",
    },
  ];

  const DATE_TIME_ZONE = "America/Sao_Paulo";
  const monthHeaderFormatter = new Intl.DateTimeFormat("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: DATE_TIME_ZONE,
  });
  const monthBadgeFormatter = new Intl.DateTimeFormat("pt-BR", {
    month: "short",
    timeZone: DATE_TIME_ZONE,
  });
  const fullDateFormatter = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "long",
    year: "numeric",
    timeZone: DATE_TIME_ZONE,
  });
  const keyFormatter = new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone: DATE_TIME_ZONE,
  });

  const toDate = (value) => new Date(`${value}T12:00:00-03:00`);

  const toDateKey = (value) => keyFormatter.format(value);

  const formatMonthHeader = (value) => {
    const label = monthHeaderFormatter.format(value);
    return label.charAt(0).toUpperCase() + label.slice(1);
  };

  const formatMonthBadge = (value) => {
    const label = monthBadgeFormatter.format(value).replace(".", "");
    return label.charAt(0).toUpperCase() + label.slice(1, 3);
  };

  const buildDateItem = (entry) => {
    const date = toDate(entry.date);

    const article = document.createElement("article");
    article.className = "important-date-item";

    const badge = document.createElement("div");
    badge.className = "important-date-badge";
    badge.setAttribute("aria-label", fullDateFormatter.format(date));

    const day = document.createElement("span");
    day.className = "important-date-day";
    day.textContent = entry.date.slice(8, 10);

    const month = document.createElement("span");
    month.className = "important-date-month";
    month.textContent = formatMonthBadge(date);

    badge.append(day, month);

    const copy = document.createElement("div");
    copy.className = "important-date-copy";

    const audience = document.createElement("p");
    audience.className = "important-date-category";
    audience.textContent = entry.audience;

    const description = document.createElement("p");
    description.className = "important-date-description";
    description.textContent = entry.description;

    copy.append(audience, description);
    article.append(badge, copy);
    return article;
  };

  const render = () => {
    const todayKey = toDateKey(new Date());
    const upcomingEntries = IMPORTANT_DATES.filter((entry) => entry.date >= todayKey);

    listElement.replaceChildren();

    if (upcomingEntries.length === 0) {
      listElement.hidden = true;
      if (emptyElement) {
        emptyElement.hidden = false;
      }
      return;
    }

    listElement.hidden = false;
    if (emptyElement) {
      emptyElement.hidden = true;
    }

    const groupedEntries = upcomingEntries.reduce((groups, entry) => {
      const key = entry.date.slice(0, 7);
      if (!groups.has(key)) {
        groups.set(key, []);
      }
      groups.get(key).push(entry);
      return groups;
    }, new Map());

    groupedEntries.forEach((entries, monthKey) => {
      const group = document.createElement("section");
      group.className = "important-date-group";
      group.setAttribute("aria-label", monthKey);

      const title = document.createElement("h3");
      title.className = "important-date-group-title";
      title.textContent = formatMonthHeader(toDate(`${monthKey}-01`));

      group.appendChild(title);
      entries.forEach((entry) => {
        group.appendChild(buildDateItem(entry));
      });
      listElement.appendChild(group);
    });
  };

  render();
})();
