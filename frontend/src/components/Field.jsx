import { useId } from 'react';

/** Label + input + inline error message wired together for accessibility. */
export function Field({ label, error, hint, children }) {
  const id = useId();
  const errorId = `${id}-error`;
  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': error ? 'true' : undefined, 'aria-describedby': error ? errorId : undefined })}
      {hint && !error ? <small className="hint">{hint}</small> : null}
      {error ? <small id={errorId} className="field-error">{error}</small> : null}
    </div>
  );
}

export function TextField({ label, error, hint, ...inputProps }) {
  return <Field label={label} error={error} hint={hint}>{(a11y) => <input {...a11y} {...inputProps} />}</Field>;
}

export function SelectField({ label, error, hint, options, ...selectProps }) {
  return (
    <Field label={label} error={error} hint={hint}>
      {(a11y) => (
        <select {...a11y} {...selectProps}>
          {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      )}
    </Field>
  );
}
