#!/usr/bin/env python3
"""Painel Windows profissional para operar o servidor Minecraft via SSH."""

from __future__ import annotations

import json
import queue
import re
import shlex
import shutil
import subprocess
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from tkinter.scrolledtext import ScrolledText
from typing import Optional, Sequence


DEFAULT_HOST = "157.151.2.91"
DEFAULT_USER = "opc"
REMOTE_MC = "/usr/local/bin/mc"
CONFIG_NAME = "mc-gui.json"

COLORS = {
    "bg": "#6eb4d8",
    "sky": "#8bd0ee",
    "surface": "#343434",
    "surface_alt": "#484848",
    "stone": "#777777",
    "stone_dark": "#292929",
    "border": "#171717",
    "text": "#ffffff",
    "muted": "#d0d0d0",
    "blue": "#55d7ff",
    "green": "#55c94f",
    "red": "#d94b45",
    "amber": "#f3c64e",
    "dirt": "#80512e",
    "grass": "#5a9e3b",
}
REFRESH_SECONDS = 60
CREATE_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000)


def application_directory() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


class RemoteClient:
    def __init__(self, host: str, user: str, key: str, remote_command: str = REMOTE_MC):
        self.host = host.strip()
        self.user = user.strip()
        self.key = key.strip()
        self.remote_command = remote_command.strip() or REMOTE_MC

    def command(self, arguments: Sequence[str]) -> list[str]:
        if not self.host or not self.user or not self.key:
            raise ValueError("Preencha host, usuário e chave SSH.")
        if not Path(self.key).is_file():
            raise ValueError(f"Chave SSH não encontrada: {self.key}")
        ssh = shutil.which("ssh")
        if not ssh:
            raise RuntimeError(
                "ssh.exe não foi encontrado no PATH. Ative o OpenSSH Client do Windows."
            )
        remote = " ".join(
            [shlex.quote(self.remote_command), *(shlex.quote(str(item)) for item in arguments)]
        )
        return [
            ssh, "-i", self.key, "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15",
            "-o", "ServerAliveInterval=15", f"{self.user}@{self.host}", remote,
        ]

    def run(self, arguments: Sequence[str], timeout: Optional[float] = 90) -> tuple[int, str]:
        process = subprocess.run(
            self.command(arguments), capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=timeout, check=False,
            creationflags=CREATE_NO_WINDOW,
        )
        output = process.stdout
        if process.stderr:
            output += ("\n" if output else "") + process.stderr
        return process.returncode, output.strip()


class MinecraftGui(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Never Land Control")
        self.geometry("1180x760")
        self.minsize(940, 620)
        self.configure(bg=COLORS["bg"])
        self._tasks: queue.Queue[tuple[str, int, str, tuple[str, ...], bool]] = queue.Queue()
        self._busy = False
        self._refresh_remaining = REFRESH_SECONDS
        self._config_path = application_directory() / CONFIG_NAME
        self.host_var = tk.StringVar(value=DEFAULT_HOST)
        self.user_var = tk.StringVar(value=DEFAULT_USER)
        self.key_var = tk.StringVar()
        self.remote_command_var = tk.StringVar(value=REMOTE_MC)
        self._configure_theme()
        self._create_widgets()
        self._load_config()
        self.after(100, self._drain_tasks)
        self.after(1000, self._refresh_tick)
        self.after(1400, lambda: self._run(("status",), silent=True))

    def _configure_theme(self) -> None:
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure(".", background=COLORS["bg"], foreground=COLORS["text"],
                        font=("Consolas", 10))
        style.configure("TFrame", background=COLORS["bg"])
        style.configure("Surface.TFrame", background=COLORS["surface"])
        style.configure("Sidebar.TFrame", background=COLORS["stone_dark"])
        style.configure("TLabel", background=COLORS["bg"], foreground=COLORS["text"])
        style.configure("Muted.TLabel", foreground=COLORS["muted"], background=COLORS["bg"])
        style.configure("Surface.TLabel", foreground=COLORS["text"],
                        background=COLORS["surface"])
        style.configure("SurfaceMuted.TLabel", foreground="#e5e5e5",
                        background=COLORS["surface"])
        style.configure("Title.TLabel", font=("Consolas", 20, "bold"),
                        foreground=COLORS["text"])
        style.configure("Subtitle.TLabel", font=("Consolas", 10),
                        foreground=COLORS["muted"])
        style.configure("CardTitle.TLabel", font=("Consolas", 10, "bold"),
                        foreground=COLORS["muted"], background=COLORS["surface"])
        style.configure("CardValue.TLabel", font=("Consolas", 17, "bold"),
                        foreground=COLORS["text"])
        style.configure("TButton", background=COLORS["stone"], foreground=COLORS["text"],
                        borderwidth=2, relief="raised", padding=(12, 9),
                        font=("Consolas", 10, "bold"))
        style.map("TButton", background=[("active", COLORS["grass"]),
                                         ("disabled", COLORS["stone_dark"])])
        style.configure("Primary.TButton", background=COLORS["grass"],
                        foreground="#ffffff", font=("Consolas", 10, "bold"))
        style.map("Primary.TButton", background=[("active", "#75bd4c")])
        style.configure("Danger.TButton", background=COLORS["red"], foreground="#ffffff")
        style.map("Danger.TButton", background=[("active", "#ef6a5e")])
        style.configure("Nav.TButton", background=COLORS["stone"], foreground=COLORS["text"],
                        anchor="w", padding=(14, 11), font=("Consolas", 10, "bold"))
        style.map("Nav.TButton", background=[("active", COLORS["grass"])])
        style.configure("TEntry", fieldbackground=COLORS["stone_dark"],
                        foreground=COLORS["text"], insertcolor=COLORS["text"],
                        borderwidth=2, padding=8)
        style.configure("TNotebook", background=COLORS["bg"], borderwidth=0)
        style.configure("TNotebook.Tab", background=COLORS["surface"],
                        foreground=COLORS["muted"], padding=(14, 8))
        style.map("TNotebook.Tab", background=[("selected", COLORS["surface_alt"])],
                  foreground=[("selected", COLORS["text"])])

    def _create_widgets(self) -> None:
        self.columnconfigure(1, weight=1)
        self.rowconfigure(0, weight=1)
        self._create_sidebar()
        main = ttk.Frame(self, padding=(28, 18, 28, 12))
        main.grid(row=0, column=1, sticky="nsew")
        main.columnconfigure(0, weight=1)
        main.rowconfigure(3, weight=1)
        self._create_header(main)
        self._create_metrics(main)
        self._create_actions(main)
        self._create_console(main)
        self._create_footer(main)

    def _create_sidebar(self) -> None:
        sidebar = ttk.Frame(self, style="Sidebar.TFrame", padding=(18, 22))
        sidebar.grid(row=0, column=0, sticky="nsew")
        sidebar.configure(width=220)
        sidebar.grid_propagate(False)
        tk.Label(sidebar, text="[N]", bg=COLORS["grass"], fg="#ffffff",
                 font=("Consolas", 15, "bold"), width=4, height=1).pack(anchor="w")
        tk.Label(sidebar, text="NEVER LAND", bg=COLORS["stone_dark"], fg=COLORS["text"],
                 font=("Consolas", 14, "bold")).pack(anchor="w", pady=(14, 0))
        tk.Label(sidebar, text="BEDROCK SERVER", bg=COLORS["stone_dark"], fg=COLORS["muted"],
                 font=("Consolas", 8, "bold")).pack(anchor="w", pady=(1, 28))
        tk.Label(sidebar, text="MUNDO", bg=COLORS["stone_dark"], fg=COLORS["amber"],
                 font=("Consolas", 9, "bold")).pack(anchor="w", pady=(0, 7))
        tk.Label(sidebar, text="Painel de controle\ncom visual de inventário.",
                 bg=COLORS["stone_dark"], fg=COLORS["muted"],
                 font=("Consolas", 9), justify="left").pack(anchor="w")
        self.refresh_badge = tk.Label(sidebar, text="Próxima leitura: --",
                                      bg=COLORS["stone_dark"], fg=COLORS["blue"],
                                      font=("Consolas", 9, "bold"))
        self.refresh_badge.pack(anchor="w", pady=(12, 0))
        spacer = ttk.Frame(sidebar, style="Sidebar.TFrame")
        spacer.pack(fill="both", expand=True)
        tk.Label(sidebar, text="CANAL SSH", bg=COLORS["stone_dark"], fg=COLORS["muted"],
                 font=("Consolas", 8, "bold")).pack(anchor="w")
        self.connection_badge = tk.Label(sidebar, text="[ ] NÃO CONFIGURADO",
                                         bg=COLORS["stone_dark"], fg=COLORS["amber"],
                                         font=("Consolas", 9, "bold"))
        self.connection_badge.pack(anchor="w", pady=(5, 0))

    def _create_header(self, parent: ttk.Frame) -> None:
        header = ttk.Frame(parent)
        header.grid(row=0, column=0, sticky="ew", pady=(0, 20))
        header.columnconfigure(0, weight=1)
        grass_line = tk.Frame(header, bg=COLORS["grass"], height=14)
        grass_line.grid(row=0, column=0, columnspan=2, sticky="ew", pady=(0, 14))
        for index in range(24):
            tk.Frame(grass_line, bg=("#4f8e32" if index % 3 else "#6cad43"),
                     width=24, height=14).pack(side="left")
        ttk.Label(header, text="NEVER LAND", style="Title.TLabel").grid(
            row=1, column=0, sticky="w")
        ttk.Label(header, text="Menu de operações do Bedrock Dedicated Server",
                  style="Subtitle.TLabel").grid(row=2, column=0, sticky="w", pady=(3, 0))
        ttk.Button(header, text="Conexão SSH", command=self._open_connection).grid(
            row=1, column=1, padx=(12, 0), sticky="e")

    def _create_metrics(self, parent: ttk.Frame) -> None:
        metrics = ttk.Frame(parent)
        metrics.grid(row=1, column=0, sticky="ew", pady=(0, 18))
        for index in range(4):
            metrics.columnconfigure(index, weight=1)
        self.metric_status = self._metric(metrics, 0, "SERVER STATUS", "LENDO...", COLORS["amber"])
        self.metric_players = self._metric(metrics, 1, "PLAYERS ONLINE", "--", COLORS["blue"])
        self.metric_latency = self._metric(metrics, 2, "CHANNEL", "SSH", COLORS["green"])
        self.metric_last = self._metric(metrics, 3, "LAST OPERATION", "Ready", COLORS["muted"])

    def _metric(self, parent: ttk.Frame, column: int, title: str,
                value: str, accent: str) -> tk.Label:
        card = tk.Frame(parent, bg=COLORS["surface"], highlightbackground=COLORS["border"],
                        highlightthickness=1, padx=15, pady=12)
        card.grid(row=0, column=column, sticky="ew", padx=(0 if column == 0 else 8, 0))
        tk.Label(card, text=title, bg=COLORS["surface"], fg=COLORS["muted"],
                 font=("Consolas", 8, "bold")).pack(anchor="w")
        value_label = tk.Label(card, text=value, bg=COLORS["surface"], fg=accent,
                               font=("Consolas", 16, "bold"))
        value_label.pack(anchor="w", pady=(6, 0))
        return value_label

    def _create_actions(self, parent: ttk.Frame) -> None:
        area = ttk.Frame(parent)
        area.grid(row=2, column=0, sticky="ew", pady=(0, 18))
        area.columnconfigure(0, weight=1)
        ttk.Label(area, text="MENU PRINCIPAL", style="CardTitle.TLabel").grid(
            row=0, column=0, sticky="w", pady=(0, 8))
        actions = ttk.Frame(area, style="Surface.TFrame", padding=12)
        actions.grid(row=1, column=0, sticky="ew")
        for column in range(7):
            actions.columnconfigure(column, weight=1)
        buttons = [
            ("Status", ("status",), "Primary.TButton"),
            ("Iniciar", ("start",), "TButton"),
            ("Parar", ("stop", "-f"), "Danger.TButton"),
            ("Reiniciar", ("restart",), "TButton"),
            ("Jogadores", ("list",), "TButton"),
            ("Backup", ("backup",), "TButton"),
            ("Atualizar", ("update",), "TButton"),
            ("Boot", ("boot",), "TButton"),
            ("Logs", ("log", "50"), "TButton"),
            ("Recursos", ("recursos",), "TButton"),
            ("Rede", ("rede",), "TButton"),
            ("Configuração", ("config",), "TButton"),
            ("Backups", ("backups",), "TButton"),
        ]
        for index, (label, command, style) in enumerate(buttons):
            ttk.Button(actions, text=label, style=style,
                       command=lambda c=command: self._run(c)).grid(
                           row=index // 7, column=index % 7, padx=4, pady=4, sticky="ew")

    def _create_console(self, parent: ttk.Frame) -> None:
        console = ttk.Frame(parent, style="Surface.TFrame", padding=16)
        console.grid(row=3, column=0, sticky="nsew")
        console.columnconfigure(0, weight=1)
        console.columnconfigure(1, weight=1)
        console.rowconfigure(2, weight=1)
        ttk.Label(console, text="INFORMAÇÕES DO MUNDO", style="CardTitle.TLabel").grid(
            row=0, column=0, columnspan=2, sticky="w")
        ttk.Label(console, text="Dados coletados do servidor remoto",
                  style="Muted.TLabel").grid(row=1, column=0, columnspan=2,
                                             sticky="w", pady=(2, 12))
        details = ttk.Frame(console, style="Surface.TFrame")
        details.grid(row=2, column=0, sticky="nsew", padx=(0, 12))
        details.columnconfigure(1, weight=1)
        self.detail_vars = {}
        for row, (key, label) in enumerate((
            ("service", "Serviço"),
            ("memory", "Memória BDS"),
            ("pid", "Processo"),
            ("started", "Iniciado em"),
        )):
            self.detail_vars[key] = tk.StringVar(value="Aguardando leitura")
            ttk.Label(details, text=label, style="SurfaceMuted.TLabel").grid(
                row=row, column=0, sticky="w", pady=6)
            ttk.Label(details, textvariable=self.detail_vars[key],
                      style="Surface.TLabel").grid(
                row=row, column=1, sticky="e", pady=6)
        right = ttk.Frame(console, style="Surface.TFrame")
        right.grid(row=2, column=1, sticky="nsew")
        right.columnconfigure(0, weight=1)
        ttk.Label(right, text="CHAT / ATIVIDADE", style="CardTitle.TLabel").grid(
            row=0, column=0, sticky="w")
        self.output = ScrolledText(right, wrap="word", state="disabled",
                                   height=7, bg="#102238", fg="#dbeafe",
                                   insertbackground="#ffffff", selectbackground="#1d4ed8",
                                   relief="flat", borderwidth=0, font=("Cascadia Mono", 9),
                                   padx=10, pady=8)
        self.output.grid(row=1, column=0, sticky="nsew", pady=(8, 0))
        command_frame = ttk.Frame(console, style="Surface.TFrame")
        command_frame.grid(row=3, column=0, columnspan=2, sticky="ew", pady=(14, 0))
        command_frame.columnconfigure(0, weight=1)
        ttk.Label(command_frame, text="Comando Bedrock", style="SurfaceMuted.TLabel").grid(
            row=0, column=0, columnspan=2, sticky="w", pady=(0, 5))
        self.console_command = ttk.Entry(command_frame)
        self.console_command.grid(row=1, column=0, sticky="ew")
        self.console_command.insert(0, "say ")
        self.console_command.bind("<Return>", lambda _event: self._send_console_command())
        ttk.Button(command_frame, text="Enviar comando", style="Primary.TButton",
                   command=self._send_console_command).grid(row=1, column=1, padx=(8, 0))

    def _create_footer(self, parent: ttk.Frame) -> None:
        self.status = tk.StringVar(value="Pronto. Configure a conexão SSH para começar.")
        ttk.Label(parent, textvariable=self.status, style="Muted.TLabel").grid(
            row=4, column=0, sticky="w", pady=(10, 0))

    def _open_connection(self) -> None:
        dialog = tk.Toplevel(self)
        dialog.title("Configuração SSH")
        dialog.geometry("550x280")
        dialog.configure(bg=COLORS["bg"])
        dialog.transient(self)
        dialog.grab_set()
        dialog.columnconfigure(1, weight=1)
        fields = (("Host / IP", self.host_var), ("Usuário", self.user_var),
                  ("Chave privada", self.key_var), ("Comando remoto", self.remote_command_var))
        for row, (label, variable) in enumerate(fields):
            ttk.Label(dialog, text=label).grid(row=row, column=0, padx=18, pady=9, sticky="w")
            ttk.Entry(dialog, textvariable=variable).grid(
                row=row, column=1, padx=8, pady=9, sticky="ew")
        ttk.Button(dialog, text="Selecionar chave", command=self._choose_key).grid(
            row=2, column=2, padx=(0, 18))
        ttk.Button(dialog, text="Salvar e testar", style="Primary.TButton",
                   command=lambda: (self._save_config(), dialog.destroy(),
                                    self._run(("status",)))).grid(
                                        row=4, column=1, padx=8, pady=16, sticky="e")

    def _choose_key(self) -> None:
        selected = filedialog.askopenfilename(
            title="Selecione a chave privada SSH",
            filetypes=(("Chaves SSH", "*.key *.pem *"), ("Todos os arquivos", "*.*")),
        )
        if selected:
            self.key_var.set(selected)

    def _client(self) -> RemoteClient:
        return RemoteClient(self.host_var.get(), self.user_var.get(), self.key_var.get(),
                            self.remote_command_var.get())

    def _run(self, arguments: Sequence[str], silent: bool = False) -> None:
        if self._busy:
            self.status.set("Aguarde a operação atual terminar.")
            return
        self._busy = True
        operation = " ".join(arguments)
        self.metric_last.configure(text=operation.upper()[:18], fg=COLORS["blue"])
        self.status.set(f"Executando {operation} no servidor remoto...")
        if not silent:
            self._append(f"\n> {self.remote_command_var.get()} {operation}\n")

        def worker() -> None:
            try:
                code, output = self._client().run(arguments)
                self._tasks.put(("result", code, output, tuple(arguments), silent))
            except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
                self._tasks.put(("error", 1, str(error), tuple(arguments), silent))

        threading.Thread(target=worker, daemon=True).start()

    def _send_console_command(self) -> None:
        command = self.console_command.get().strip()
        if not command:
            return
        self.console_command.delete(0, tk.END)
        self._run(("cmd", command))

    def _focus_console(self) -> None:
        self.console_command.focus_set()

    def _refresh_tick(self) -> None:
        if not self._busy:
            self._refresh_remaining -= 1
        if self._refresh_remaining <= 0 and not self._busy:
            self._refresh_remaining = REFRESH_SECONDS
            self._run(("status",), silent=True)
        self.refresh_badge.configure(
            text=f"Próxima leitura: {max(0, self._refresh_remaining)}s")
        self.after(1000, self._refresh_tick)

    def _update_status_details(self, output: str) -> None:
        def find(pattern: str, default: str = "Não informado") -> str:
            match = re.search(pattern, output, re.IGNORECASE)
            return match.group(1).strip() if match else default

        service_match = re.search(
            r"Serviço Systemd:\s*\[([^\]]+)\]\s*(?:\(([^)]*)\))?",
            output,
            re.IGNORECASE,
        )
        service_state = service_match.group(1).strip() if service_match else "NÃO INFORMADO"
        service_substate = service_match.group(2).strip() if service_match and service_match.group(2) else ""
        service = service_state + (f" ({service_substate})" if service_substate else "")
        memory = find(r"Memória RAM \(BDS\):\s*(.*)")
        pid = find(r"PID do Processo:\s*(.*)")
        started = find(r"Data de Início:\s*(.*)")
        self.detail_vars["service"].set(service)
        self.detail_vars["memory"].set(memory)
        self.detail_vars["pid"].set(pid)
        self.detail_vars["started"].set(started)
        player_line = find(r"There are\s+(\d+/\d+\s+players online)", "--")
        self.metric_players.configure(text=player_line)
        active = "ATIVO" if any(
            marker in service_state.upper()
            for marker in ("ATIVO", "OPERACIONAL", "RUNNING", "ACTIVE")
        ) else "OFFLINE"
        self.metric_status.configure(text=active,
                                     fg=COLORS["green"] if active == "ATIVO" else COLORS["amber"])

    def _append(self, text: str) -> None:
        self.output.configure(state="normal")
        self.output.insert(tk.END, text)
        self.output.see(tk.END)
        self.output.configure(state="disabled")

    def _drain_tasks(self) -> None:
        try:
            while True:
                kind, code, output, arguments, silent = self._tasks.get_nowait()
                self._busy = False
                if arguments == ("status",) and kind == "result" and code == 0:
                    self._update_status_details(output)
                    self._refresh_remaining = REFRESH_SECONDS
                    self.metric_last.configure(text="STATUS", fg=COLORS["green"])
                    self.status.set("Status atualizado com sucesso.")
                elif not silent:
                    self._append((output or "(sem saída)") + "\n")
                if kind == "error":
                    self.connection_badge.configure(text="●  CONNECTION ERROR", fg=COLORS["red"])
                    self.status.set("Falha na operação. Verifique a conexão SSH.")
                    messagebox.showerror("Operação remota", output)
                else:
                    self.connection_badge.configure(text="●  CONNECTED", fg=COLORS["green"])
                    if arguments != ("status",):
                        self.status.set("Operação concluída." if code == 0
                                        else f"Operação retornou código {code}.")
                        self.metric_last.configure(
                            text="OK" if code == 0 else "ATENÇÃO",
                            fg=COLORS["green"] if code == 0 else COLORS["amber"])
        except queue.Empty:
            pass
        self.after(100, self._drain_tasks)

    def _load_config(self) -> None:
        default_keys = (
            application_directory() / "VM" / "SSH keys" / "ssh-key-2026-11-03.key",
            application_directory().parent / "VM" / "SSH keys" / "ssh-key-2026-11-03.key",
        )
        if not self.key_var.get():
            for default_key in default_keys:
                if default_key.is_file():
                    self.key_var.set(str(default_key))
                    break
        if self._config_path.is_file():
            try:
                values = json.loads(self._config_path.read_text(encoding="utf-8"))
                for variable, name in ((self.host_var, "host"), (self.user_var, "user"),
                                       (self.key_var, "key"),
                                       (self.remote_command_var, "remote_command")):
                    if values.get(name):
                        variable.set(values[name])
            except (OSError, ValueError):
                self.status.set("Não foi possível ler a configuração salva.")

    def _save_config(self) -> None:
        values = {"host": self.host_var.get().strip(), "user": self.user_var.get().strip(),
                  "key": self.key_var.get().strip(),
                  "remote_command": self.remote_command_var.get().strip()}
        try:
            self._config_path.write_text(json.dumps(values, indent=2), encoding="utf-8")
            self.status.set("Configuração SSH salva com segurança.")
        except OSError as error:
            messagebox.showerror("Configuração", f"Não foi possível salvar: {error}")


if __name__ == "__main__":
    app = MinecraftGui()
    app.mainloop()
