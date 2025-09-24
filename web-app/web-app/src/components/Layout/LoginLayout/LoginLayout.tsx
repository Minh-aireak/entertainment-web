import React from "react";

export default function LoginLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const style: React.CSSProperties = {
    position: "relative",
    backgroundImage: 'url("/LoginImage.jpg")',
    backgroundSize: "cover",
    backgroundPosition: "center",
    width: "100%",
    height: "100vh",
  };

  return <main style={style}>{children}</main>;
}
