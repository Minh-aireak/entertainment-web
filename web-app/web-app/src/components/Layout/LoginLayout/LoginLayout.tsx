import styles from "./LoginLayout.module.scss";
import classNames from "classnames/bind";

const sx = classNames.bind(styles);

export default function LoginLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <main className={sx("wrapper")}>{children}</main>;
}
