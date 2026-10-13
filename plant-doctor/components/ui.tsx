import type { ReactNode } from "react";

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-2xl border border-border bg-surface p-4 shadow-sm ${className}`}>
      {children}
    </section>
  );
}

export function CardTitle({ children, icon }: { children: ReactNode; icon?: string }) {
  return (
    <h2 className="mb-3 flex items-center gap-2 text-lg font-bold">
      {icon && <span aria-hidden="true">{icon}</span>}
      {children}
    </h2>
  );
}

export function Button({
  children,
  onClick,
  variant = "primary",
  disabled,
  type = "button",
  className = "",
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: "primary" | "secondary" | "ghost";
  disabled?: boolean;
  type?: "button" | "submit";
  className?: string;
}) {
  const styles = {
    primary: "bg-accent text-accent-contrast hover:bg-accent-strong",
    secondary: "border border-border bg-surface text-text hover:bg-surface-2",
    ghost: "text-accent underline-offset-4 hover:underline",
  }[variant];
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`min-h-11 rounded-xl px-5 py-2.5 font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${styles} ${className}`}
    >
      {children}
    </button>
  );
}

export function Chips<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ value: T; label: string }>;
  value: T | null;
  onChange: (v: T | null) => void;
}) {
  return (
    <fieldset className="mb-4">
      <legend className="mb-2 text-sm font-semibold text-muted">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((o) => {
          const active = value === o.value;
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(active ? null : o.value)}
              className={`min-h-11 rounded-full border px-4 text-sm transition-colors ${
                active ? "border-accent bg-accent text-accent-contrast" : "border-border bg-surface hover:bg-surface-2"
              }`}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}

export function MultiChips<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ value: T; label: string }>;
  value: T[];
  onChange: (v: T[]) => void;
}) {
  return (
    <fieldset className="mb-4">
      <legend className="mb-2 text-sm font-semibold text-muted">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((o) => {
          const active = value.includes(o.value);
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(active ? value.filter((v) => v !== o.value) : [...value, o.value])}
              className={`min-h-11 rounded-full border px-4 text-sm transition-colors ${
                active ? "border-accent bg-accent text-accent-contrast" : "border-border bg-surface hover:bg-surface-2"
              }`}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}

export function TextField({
  label,
  value,
  onChange,
  placeholder,
  multiline,
}: {
  label: string;
  value: string | null;
  onChange: (v: string | null) => void;
  placeholder?: string;
  multiline?: boolean;
}) {
  const common =
    "w-full rounded-xl border border-border bg-surface px-3 py-2.5 text-base placeholder:text-muted/70 focus:border-accent focus:outline-none";
  return (
    <label className="mb-4 block">
      <span className="mb-2 block text-sm font-semibold text-muted">{label}</span>
      {multiline ? (
        <textarea
          rows={3}
          className={common}
          value={value ?? ""}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value || null)}
        />
      ) : (
        <input
          className={`${common} min-h-11`}
          value={value ?? ""}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value || null)}
        />
      )}
    </label>
  );
}
